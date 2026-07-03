# 日志系统设计文档

> 目标：统一全 App 日志入口，支持本地持久化（文件落盘 + 轮转）与按需上云，
> 让线上问题可以拿到「事发前后完整的本地日志」，而不只是 Sentry 里的孤立异常。

## 1. 背景与现状

当前三套设施并存、各管一段，互不打通：

| 现有设施 | 位置 | 能力 | 缺口 |
|---|---|---|---|
| `LogUtils` | `util/` | Logcat 封装，Release 静默低级别 | 不落盘，Release 日志即丢 |
| `DebugLog` | `common/log/` | 内存环形缓冲 500 条，Debug 工具箱可看/导出 | 仅 Debug 包、进程死即丢 |
| `SentryUtils` | `util/` | 异常/结构化日志/指标上云 | 只有「上报那一刻」的信息，无本地上下文 |

核心痛点：**用户反馈「昨天笔记丢了」这类问题时，拿不到当时的本地日志。**

## 2. 目标与非目标

**目标**

- 统一入口：一个 `AppLog` 门面，替代 `LogUtils`/`DebugLog` 的直接调用；
- 本地持久化：全级别落盘，轮转 + 过期清理，崩溃也不丢最后几条；
- 按需上云：用户反馈附带上传、服务端指令拉取（FCM）、崩溃自动附带；
- 低开销：不阻塞调用线程，Release 包 CPU/IO 开销可忽略；
- 隐私安全：落盘即脱敏，上传加密。

**非目标**

- 不替代 Sentry：崩溃、性能、指标仍走 Sentry，本系统专注「运行日志」；
- 不做实时日志流（远程 tail）；
- 不引入 NDK/C++（见 §8 选型）。

## 3. 总体架构

```
调用方（业务代码 / OkHttp 拦截器 / 未捕获异常钩子）
        │  AppLog.i(tag) { "msg" }   ← 惰性求值，级别不够不构造字符串
        ▼
┌─ AppLog（门面，object）───────────────────────────┐
│  级别过滤（Release ≥ INFO，可远程配置调低）        │
│  档位路由（Policy：不落盘/落盘/上云，见 §4.1）     │
│  脱敏（PII Masker：手机号/邮箱/token 正则替换）    │
└──────────────┬────────────────────────────────────┘
               │ LogEvent(time, level, tag, msg, tid, policy)
               ▼
        Channel(BUFFERED)  ← 单一背压点，满则丢 VERBOSE/DEBUG
               │  单消费者协程（Dispatchers.IO）
     ┌─────────┼──────────────┬───────────────┐
     ▼         ▼              ▼               ▼
LogcatSink  MemorySink     FileSink       CloudRelaySink
(仅 Debug)  (环形 500 条,   (LOCAL/CLOUD    (仅 CLOUD 档 →
            Debug 工具箱)   档，落盘+轮转)   Sentry 实时上报)
                               │
                               ▼
                     UploadManager（按需打包上传，见 §6）
```

设计要点：

- **门面 + Sink 管道**：新增输出端只加 Sink，调用方零改动（借鉴 Timber 的
  Tree 模型）；
- **单消费者协程**：所有 Sink 串行消费同一 Channel，天然免锁；文件写入
  只有一个写者，无并发损坏问题；
- **惰性消息**：`AppLog.d(tag) { "expensive $obj" }`，级别被过滤时 lambda
  不执行，Release 包 DEBUG 日志零成本。

## 4. API 设计

### 4.1 三档输出策略（Policy）

每条日志归入三档之一，档位**逐级叠加**（上云必先落盘，落盘必出 Logcat）：

```
TRANSIENT（不落盘） ⊂ LOCAL（落盘到本地） ⊂ CLOUD（上云）
```

| 档位 | 输出到 | 适用场景 |
|---|---|---|
| `TRANSIENT` | Logcat + 内存环形缓冲 | 开发调试、高频噪音（滚动/重组）、含敏感信息不宜留存的 |
| `LOCAL` | + 文件落盘 | **默认档**：业务流水、状态变迁，供事后按需拉取 |
| `CLOUD` | + 实时上报 Sentry | 关键业务事件（登录/支付/数据迁移）、需要实时告警的错误 |

档位与级别是两个正交维度：级别决定「重要程度/是否过滤」，档位决定
「去哪」。默认映射（可按条覆盖）：

| 级别 | 默认档位 | Debug 包 | Release 包 |
|---|---|---|---|
| V / D | `TRANSIENT` | Logcat + 内存 | 丢弃 |
| I | `LOCAL` | 全部本地 Sink | 文件 |
| W | `LOCAL` | 全部本地 Sink | 文件 + Sentry breadcrumb |
| E | `CLOUD` | 全部 Sink | 文件 + Sentry capture |

覆盖示例：

```kotlin
// 高频 D 日志显式降为不落盘（默认 D 本就 TRANSIENT，示意写法）
AppLog.d("Editor", policy = Policy.TRANSIENT) { "recompose $count" }

// 普通 I 日志升为上云：关键业务节点，需要实时可见
AppLog.i("Auth", policy = Policy.CLOUD) { "login success, method=$method" }
```

### 4.2 接口

```kotlin
enum class Policy { TRANSIENT, LOCAL, CLOUD }

object AppLog {
    fun v(tag: String, policy: Policy = Policy.TRANSIENT, msg: () -> String)
    fun d(tag: String, policy: Policy = Policy.TRANSIENT, msg: () -> String)
    fun i(tag: String, policy: Policy = Policy.LOCAL, msg: () -> String)
    fun w(tag: String, tr: Throwable? = null, policy: Policy = Policy.LOCAL, msg: () -> String)
    fun e(tag: String, tr: Throwable? = null, policy: Policy = Policy.CLOUD, msg: () -> String)

    /** 结构化事件：一条日志携带完整上下文，与 SentryUtils.logEvent 对齐。 */
    fun event(tag: String, name: String, attrs: Map<String, Any?> = emptyMap(), policy: Policy = Policy.CLOUD)

    /** flush 缓冲并返回当前日志目录（用户反馈上传前调用）。 */
    suspend fun flushAndGetLogDir(): File
}
```

实现上，`LogEvent` 携带 `policy` 字段，各 Sink 消费时自行判断是否处理：
`FileSink` 只处理 `LOCAL`/`CLOUD`，`CloudRelaySink` 只处理 `CLOUD`。

## 5. 本地持久化（FileSink）

### 5.1 目录与命名

```
filesDir/logs/
├── novie-20260703-0.log      // 当天第 0 个分片
├── novie-20260703-1.log      // 单文件超 2MB 滚动
├── novie-20260702-0.log
└── upload/                   // 打包待上传的 zip（上传成功即删）
```

### 5.2 写入策略

- `BufferedWriter`（8KB 缓冲）+ 定时 flush（5s）+ 级别触发 flush
  （W/E 立即 flush，保证崩溃前的关键日志落盘）；
- 进程异常退出兜底：注册 `Thread.setDefaultUncaughtExceptionHandler`
  链式钩子（保留 Sentry 的原钩子），崩溃时同步 flush 再抛出；
- 行格式（人可读 + 机器可解析）：

```
2026-07-03 14:02:11.482 I/Home [main] note clicked: id=abc title="Q3"
{时间} {级别}/{tag} [{线程}] {消息}
```

### 5.3 轮转与清理

| 参数 | 值 | 说明 |
|---|---|---|
| 单文件上限 | 2 MB | 超过滚动新分片 |
| 单日上限 | 10 MB | 超过丢弃最旧分片 |
| 保留天数 | 7 天 | 启动后空闲时清理（复用 RecordingCleaner 的 scheduleOnIdle 模式） |
| 磁盘保护 | 可用 < 50 MB 停写 | 与 AppConfig 现有存储阈值风格一致 |

参数全部收敛到 `AppConfig.Log`（遵循项目「魔法数字进 AppConfig」约定）。

## 6. 上云（UploadManager）

### 6.1 触发方式（三种，均为「按需」，不做全量实时上传）

```
1. 用户反馈    Settings/反馈入口 → flush → zip 最近 N 天 → 附带反馈单上传
2. 远程拉取    FCM data 消息 {"cmd":"pull_log","days":3,"taskId":"..."}
               → 静默打包上传 → 回执 taskId（排查特定用户）
3. 崩溃附带    Sentry beforeSend 钩子附最近 200 行作为 attachment
```

全量实时上云成本高且 99% 用不上；「按需拉取」是移动端主流方案
（Logan/Xlog 同思路）。

### 6.2 上传通道

- 打包：按天选分片 → zip（约 10:1 压缩比）→ 尺寸上限 30 MB；
- 传输：走现有 Retrofit/OkHttp 栈（`NetworkModule`），multipart 上传，
  携带 `CommonHeaders`（设备标识/版本），服务端以 userId + 日期归档；
- 调度：`WorkManager`（**需新增依赖 androidx.work**）——约束
  「联网 + 建议 Wi-Fi/不计费网络」，失败指数退避重试，进程死后仍能完成；
- 安全：zip 后 AES 加密可后续加（服务端 HTTPS 已保证传输层）。

### 6.3 与 Sentry 的分工

| | 本系统 | Sentry |
|---|---|---|
| 定位 | 完整运行日志（What happened around） | 异常/性能/指标（What broke） |
| 数据量 | MB 级文件，按需上传 | 事件级，实时采样上报 |
| 关联 | zip 内含 deviceId/版本，可与 Sentry 事件按时间对齐 | breadcrumb 引用本地日志时间戳 |

## 7. 隐私与安全

- **落盘即脱敏**：进 Channel 前统一过 `PiiMasker`——手机号、邮箱、
  token/JWT、身份证号正则替换为 `***`；宁可多脱不可漏脱；
- 不记录笔记正文内容（只记 id / 长度 / 操作类型）；
- 日志目录在 `filesDir`（应用私有），不落公共存储；
- 备份排除：`backup_rules.xml` 排除 `logs/`，避免云备份携带；
- 用户反馈上传前弹确认（告知将上传诊断日志）。

## 8. 选型说明：为什么自研轻量 FileSink

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 腾讯 Xlog (Mars) | mmap 防丢、加密、压缩成熟 | NDK 依赖、包体 +1MB、维护停滞 | 过重 |
| 美团 Logan | 加密 + 聚合上报方案完整 | 同样 C 实现、社区不活跃 | 过重 |
| Timber + 自定义 Tree | 门面成熟 | 仍要自己写文件 Sink，门面本身 ~100 行可自研 | 只借鉴模型 |
| **自研（本方案）** | 纯 Kotlin ~300 行、协程契合现有栈、参数进 AppConfig | mmap 级防丢不如 Xlog（用 W/E 即时 flush 补偿） | **采用** |

App 属笔记类工具，日志量中低（估 < 1 MB/天），mmap 级可靠性收益小于
NDK 引入成本；关键日志（W/E）即时 flush 已覆盖崩溃场景主要需求。

## 9. 迁移计划

1. **P0**：实现 `AppLog` + Logcat/Memory/File 三个 Sink；`DebugLog` 与
   `LogUtils` 改为委托 `AppLog`（deprecated 转发，调用方不动）；
2. **P1**：崩溃钩子 flush + Sentry attachment；Debug 工具箱增加
   「日志文件浏览/分享」（复用 FileBrowserActivity）；
3. **P2**：UploadManager + 用户反馈通道（依赖服务端上传接口就绪）；
4. **P3**：FCM 远程拉取指令 + 远程日志级别配置；
5. 收尾：删除 `LogUtils`/`DebugLog` 转发壳，全量替换调用点。

## 10. 已知限制与后续

- 不防「杀进程瞬间」最后 5s 内 V/D/I 日志丢失（缓冲未 flush）；接受，
  关键路径用 W/E 或手动 flush；
- 远程日志级别配置依赖后续的配置下发通道（可先用 FCM data 消息简版）；
- 多进程（`:web` WebView 进程）暂不接入，各进程独立文件后续再合并。
