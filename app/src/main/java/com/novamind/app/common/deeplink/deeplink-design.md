# Deep Link / App Links 设计文档

> 目标：用户在浏览器（或短信、邮件等）点击 `https://app.novamind-labs.ai/...` 链接时，
> 直接调起本应用并落到对应页面，无需经过「用浏览器还是 App 打开」的选择弹窗。

## 1. 总体流程

```
网页/外部链接 (https://app.novamind-labs.ai/home)
        │  系统安装时已通过 assetlinks.json 校验 → 直接调起 App
        ▼
MainActivity (singleTask, VIEW intent)
        │  冷启动: onCreate / 已运行: onNewIntent
        ▼
DeepLinks.resolve(intent) ──→ 目标 route（非本域名/非 VIEW → null，不导航）
        │
        ▼
deepLinkRoute (Compose state)
        │  LaunchedEffect 消费：切 currentRoute、退出覆盖层、清空防重复
        ▼
目标页面（当前仅 Home）
```

## 2. 决策流程：用户在浏览器点击 App Link

从点击到落地目标页，每一步的系统/应用决策：

```
用户点击 https://app.novamind-labs.ai/home
        │
        ▼
系统查找声明了该 host 的 VIEW intent-filter
        │
   本 App 已安装？
        │否 → 浏览器直接打开网页（网页端兜底，不中断）
        │是
        ▼
assetlinks.json 校验通过？（安装时 autoVerify 的结果）
        │是 → 直接调起 App，无弹窗
        │否 → 弹「打开方式」选择器（App / 浏览器）
        │         │ 用户选浏览器 → 打开网页，流程结束
        │         │ 用户选 App ↓
        ▼
App 进程已在运行？（MainActivity 为 singleTask）
        │否（冷启动）→ onCreate 解析 intent
        │是（热启动）→ 复用实例，onNewIntent 更新 deepLinkRoute
        ▼
DeepLinks.resolve(intent)
        │非 VIEW / 非本域名 → null，不导航（保持现状）
        │未知路径          → Home（兜底）
        │命中路由表        → 目标 route
        ▼
LaunchedEffect(deepLinkRoute) 消费
    切 currentRoute · 退出覆盖层 · 清 editingNoteId · 置 null 防重复
        ▼
认证门控（最顶层）
        │已登录   → 直达目标页
        │未登录   → 先见登录页，登录后即落在目标页
        │待指纹解锁 → 先解锁，解锁后落在目标页
```

关键决策点小结：

| 决策点 | 谁决策 | 依据 |
|---|---|---|
| 用 App 还是浏览器打开 | Android 系统 | 安装状态 + autoVerify 校验结果 |
| 冷启动还是复用实例 | Android 系统 | `singleTask` + 进程存活状态 |
| 落到哪个页面 | `DeepLinks.resolve` | URI path 首段（唯一路由表） |
| 是否立即可见 | 认证门控 | 登录 / 指纹解锁状态 |

## 3. 组成部分

### 3.1 服务端：assetlinks.json（域名所有权证明）

- 部署于 `https://app.novamind-labs.ai/.well-known/assetlinks.json`；
- 声明包名 + 签名证书 SHA-256 指纹，系统安装 App 时自动校验；
- 校验通过后该域名的 https 链接默认由本 App 打开（App Links 与普通
  Deep Link 的关键差别）；
- **注意**：换签名（如 debug/release、更换发布证书）需同步更新指纹，
  否则校验失败，链接退化为浏览器打开。

### 3.2 Manifest：intent-filter（`AndroidManifest.xml`）

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="app.novamind-labs.ai" />
</intent-filter>
```

- `autoVerify="true"`：触发安装时的 assetlinks 校验；
- **不写 path 过滤**：整个 host 全部命中，具体页面统一由 `DeepLinks` 解析。
  好处是新增入口零 manifest 改动，避免 manifest 与代码两处路由表漂移；
- `MainActivity` 为 `singleTask`：App 已在运行时点链接复用现有实例，
  走 `onNewIntent` 而非重建，页面状态不丢。

### 3.3 客户端解析：`DeepLinks`（本目录）

- 唯一路由表：URI path 首段 → `BottomNavDestination` route；
- 双入口：`resolve(Intent?)`（校验 ACTION_VIEW）与 `resolve(Uri?)`（供测试）；
- 非本域名 / 无 data → 返回 `null`，调用方不导航；
- 未知路径 → **兜底进首页**，保证外链永远打得开。

### 3.4 消费方：`MainActivity`

- `deepLinkRoute: MutableState<String?>` 字段：`onCreate`（冷启动）与
  `onNewIntent`（热启动）写入；
- Compose 侧 `LaunchedEffect(deepLinkRoute)` 消费：切 `currentRoute`、
  关闭覆盖层（Ask Novie 等）、清空 `editingNoteId`，消费后置 null 防重复触发；
- 认证门控在最顶层独立渲染：未登录时先见登录页，登录后即落在目标页。

## 4. 路由映射表

| URL | 目标页 |
|---|---|
| `https://app.novamind-labs.ai` | Home |
| `https://app.novamind-labs.ai/home` | Home |
| `https://app.novamind-labs.ai/<未知路径>` | Home（兜底） |

## 5. 新增一个 Deep Link 入口

只需一步：在 `DeepLinks.resolve` 的 `when` 里加一行，例如：

```kotlin
"library" -> BottomNavDestination.Library.route
```

manifest 与 MainActivity 均无需改动。若目标页不是底部导航 tab
（如笔记详情），需扩展 resolve 的返回类型携带参数（见 §7）。

## 6. 测试

```bash
# 模拟浏览器点击（已安装 App 且校验通过时直接调起）
adb shell am start -a android.intent.action.VIEW \
  -d "https://app.novamind-labs.ai/home" com.novamind.app

# 查看 App Links 校验状态
adb shell pm get-app-links com.novamind.app
```

单测入口：`DeepLinks.resolve(Uri?)` 为纯函数，可直接断言 URI → route 映射。

## 7. 已知限制与后续

- 仅支持底部导航 tab 级跳转；带参数的页面（如 `/note/<id>` 直达笔记）
  需把返回值从 `String` 扩展为携带参数的 sealed 类型，并在 MainActivity
  消费处补路由逻辑；
- 未接入自定义 scheme（如 `novamind://`）：App Links 已覆盖主场景，
  自定义 scheme 无域名校验、易被劫持，暂不引入；
- 未做延迟深链（deferred deep link，未安装 → 商店 → 安装后落地目标页），
  需要时可接 Firebase Dynamic Links 替代方案或 Install Referrer。
