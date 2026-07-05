# HTTP 网络封装设计

- **日期**：2026-07-04（基于现有代码总结）
- **位置**：`common/net/`（含子包 `common/net/response/`），Debug/Release 变体各有 `HttpLoggers`
- **技术栈**：OkHttp + Retrofit + kotlinx.serialization；少量历史接口走 `HttpURLConnection`
- **相关**：后端统一响应约定见 `../../my-novie-backend/doc/api-reference.md`

---

## 一、总体设计

网络层围绕「**一个 OkHttp 客户端 + 按域名分建的 Retrofit + 统一响应信封**」组织，目标是让业务侧只关心「调哪个接口、拿什么领域模型」，而把公用头部、鉴权、TLS、环境切换、日志、错误归一等横切关注点收敛到基础设施里。

分层自上而下：

1. **接口定义层**（`AuthApi` / `FilesApi` / `ApiService`）：Retrofit 接口，返回 `Response<ApiResponse<T>>`。
2. **仓库层**（如 `FilesRepository`、`feature/auth/ProfileRepository`）：调接口、用 `apiCall` 解包成 `ApiResult`，对上只暴露领域模型。
3. **基础设施层**（`NetworkModule` + 拦截器 + `CommonHeaders` + `ApiTls` + `ApiConfig` + `response/`）：构建客户端、注入横切能力、归一响应。

---

## 二、目录与职责

| 文件 | 职责 |
|------|------|
| `NetworkModule` | 单例组装 `OkHttpClient` 与按域名的 `Retrofit`，对外暴露 `apiService` / `authApi` / `filesApi`；集中 JSON 配置 |
| `ApiConfig` | 环境（`test` / `prod`）与唯一 api 域名管理，可持久化切换 |
| `CommonHeaders` | HTTPS 公用请求头（平台/版本/设备/时区/链路追踪/UA），供拦截器与 `HttpURLConnection` 复用 |
| `CommonHeadersInterceptor` | 把公用头注入每个 OkHttp 请求（不覆盖调用方已设的同名头） |
| `AuthInterceptor` | 同步附加 `Authorization: Bearer <token>` |
| `TokenProvider` | 内存态 access token 单例，供拦截器同步读取 |
| `ApiTls` | 对无 SAN 的 IP 接口做主机名钉定（证书指纹校验，非 trust-all） |
| `AuthApi` / `FilesApi` / `ApiService` | 认证域 / 文件域 / 通用（`@Url`）接口定义 |
| `response/ApiResponse` `BizCode` `ApiResult` `ApiCall` | 统一响应信封、业务码表、领域三态结果、解包器 |
| `HttpLoggers`（debug/release 各一份） | 日志拦截器：Debug 打印 body，Release 空实现 |

---

## 三、环境与域名（ApiConfig）

`ApiConfig` 用枚举 `Env`（`TEST` / `PROD`）维护一张 `Map<Env, String>` 表，每套环境仅一个 api 域名（**以 `/` 结尾**便于与相对路径拼接）。当前环境经 MMKV 持久化，`init()` 在 `Application.onCreate`（须在 MMKV 初始化后）载入，`select()` 切换并持久化；同时以 `envFlow` 暴露为可观察 `StateFlow`。

早期曾按 auth / chat / api 分三套域名，现已收敛为单一 `apiBaseUrl`——认证、文件等接口统一走这一个域。由于 Retrofit 懒加载，**切换环境需下次冷启动生效**。

---

## 四、客户端构建（NetworkModule）

`NetworkModule` 是进程级单例：

- **单一 OkHttpClient**：统一超时（取自 `AppConfig.Network`）、主机名校验（`ApiTls.PINNED_HOSTNAME_VERIFIER`）、拦截器链、按变体注入的日志拦截器。所有域名的 Retrofit 共用这同一个 client。
- **拦截器顺序**：`CommonHeadersInterceptor` → `AuthInterceptor` →（Debug）`HttpLoggingInterceptor`。日志放最后，能打印到最终发出的头。
- **统一 JSON**：一份 `Json { ignoreUnknownKeys=true; explicitNulls=false; coerceInputValues=true }`，既做 Retrofit 的 converter，也被响应解包器（`internal` 暴露）复用来解析错误信封，保证「进出同一套解析口径」。
- **建 Retrofit**：`retrofit(url)` 用同一 client + kotlinx.serialization converter；`apiService`（`apiBaseUrl`，历史 `@Url` 用）、`authApi` 与 `filesApi` 均走 `apiBaseUrl`。

---

## 五、拦截器与鉴权

**公用头注入**（`CommonHeadersInterceptor`）：复用 `CommonHeaders.snapshot()` 的字段口径，**仅当请求未显式设同名头时才补**，不覆盖 Retrofit `@Header` 的定制值。

**鉴权**（`AuthInterceptor` + `TokenProvider`）：OkHttp 拦截器是同步的，不能在其中调用会弹生物识别的挂起取凭证方法。因此约定由**认证层**在登录成功 / 静默续期后，把 access token 写入内存单例 `TokenProvider.accessToken`；`AuthInterceptor` 同步读取并加 `Authorization: Bearer <token>`（已带该头的请求不覆盖），登出时置空。这样上传录音等接口无需各自处理 token——放进拦截器即可。

---

## 六、公用请求头（CommonHeaders）

`CommonHeaders` 统一 HTTPS 头口径，自定义头一律 `X-` 前缀（`X-App-Platform` / `X-App-Version` / `X-App-Build` / `X-Device-Id` / `X-Device-Model` / `X-Device-Screen` / `X-OS-Version` / `X-OS-Api` / `X-Timezone` / `X-Request-Id`）。设计要点：

- **静态字段缓存**：平台/版本/设备/屏幕/UA 首次构建后缓存复用（与 `DeviceIdentity` 一致）。
- **动态字段每请求重算**：`X-Request-Id`（每请求唯一，链路追踪）、`Authorization`（按登录态）、`Accept-Language`（按 Locale）。
- **不设 `Content-Type`**：请求体类型由调用方决定（JSON / multipart），公用头不越权。
- **双通道复用**：既通过 `snapshot()` 供 OkHttp 拦截器使用，也通过 `apply(conn)` 供 `HttpURLConnection` 路径使用，两条网络路径头口径一致。

---

## 七、TLS 主机名钉定（ApiTls）

部分接口服务器证书**无 SAN、CN 仅含 IP**，默认主机名校验会失败。`ApiTls` 只对该 `PINNED_HOST` 放宽「主机名绑定」，改为**校验服务器证书 SHA-256 指纹**与钉定值一致；证书链校验仍由 `network_security_config` 完整执行（**非 trust-all**）。其它主机走系统默认校验。该校验器同时供 OkHttp（`hostnameVerifier`）与 `HttpURLConnection`（`apply(conn)`）复用。

---

## 八、统一响应（response/）

对齐后端「`{code, message, data}` 信封」约定（详见后端 api-reference §1）：

- **`ApiResponse<T>`**：成功/错误信封；`ApiErrorData(traceId, fields, current)`、`FieldError`。
- **`BizCode`**：业务码表（`成功=200`，`错误=HTTP×100+子码`）与工具（`httpStatusOf` / `subCodeOf` / `isAuthExpired` / `isClientError` / `isServerError`）。
- **`ApiResult`**：领域三态 `Success` / `BizError`(带 code/traceId/fields) / `NetworkError`，配 `map` / `onSuccess` / `onError` / `getOrNull` 等算子。
- **`apiCall { }` / `Response.toApiResult()`**：把 `Response<ApiResponse<T>>` 折叠为 `ApiResult`，覆盖 2xx 成功、204/空体成功、非 2xx 错误信封解析、异常/解析失败退化为 `NetworkError`。

如此业务侧只需 `when` 三个分支即可覆盖「成功 / 后端业务错误 / 没连上」，无需各自判 HTTP 码、解错误体、catch 异常。

---

## 九、接口定义约定

- **相对路径 vs `@Url`**：类型化接口（`AuthApi` / `FilesApi`）用相对路径、不带前导斜杠（baseUrl 已以 `/` 结尾）；`ApiService` 面向运行时决定的绝对地址，用 `@Url` 传入，响应保留 `ResponseBody` 原文。
- **返回类型**：业务接口统一 `suspend fun x(): Response<ApiResponse<T>>`，返回 `Response` 以便读状态码，交给 `apiCall` 解包。
- **DTO**：`@Serializable`，字段映射用 `@SerialName`；解析容忍未知字段。
- **直传对象存储**（`FilesApi.uploadToStorage`）：S3 presigned POST 直连对象存储，**不走信封**，用 `@Multipart` + `@PartMap`（policy 字段在前）+ `@Part file`，返回原始 `Response<ResponseBody>` 自行判 `isSuccessful`。

---

## 十、典型调用范式

```kotlin
// 接口
@POST("api/v1.0/files/presign")
suspend fun presign(@Body body: PresignReq): Response<ApiResponse<PresignResp>>

// 仓库：解包为领域三态
when (val r = apiCall { NetworkModule.filesApi.presign(req) }) {
    is ApiResult.Success      -> useData(r.data)
    is ApiResult.BizError     -> if (r.isAuthExpired) relogin() else toast(r.message)
    is ApiResult.NetworkError -> toast("网络异常，请重试")
}
```

Authorization、公用头、TLS、日志全部由基础设施自动处理，仓库不感知。

---

## 十一、两条网络路径并存

主线是 **OkHttp + Retrofit**（新代码一律走此路）。另有少量历史/调试接口走 **`HttpURLConnection`**（如接口测试箱、图片上传测试），通过 `CommonHeaders.apply(conn)` + `ApiTls.apply(conn)` 复用同一套头与 TLS 口径。两者共享 `CommonHeaders` / `ApiTls` / `TokenProvider`，避免口径分裂。新接口应优先走 Retrofit 主线。

---

## 十二、变体差异（Debug / Release）

`HttpLoggers` 在 `src/debug` 与 `src/release` 各有一份签名一致的实现：Debug 返回打印 body 的 `HttpLoggingInterceptor`（logging-interceptor 仅以 `debugImplementation` 引入），Release 返回 `null`。`NetworkModule` 统一 `HttpLoggers.create()?.let(::addInterceptor)`，**保证发布包不含网络明文日志**。

---

## 十三、约定与规范

- 新业务接口：类型化 Retrofit + 相对路径 + `Response<ApiResponse<T>>` + `apiCall` 解包。
- 鉴权只走 `TokenProvider`/`AuthInterceptor`，不在业务里手拼 `Authorization`。
- 公用头只在 `CommonHeaders` 增删，自定义头保持 `X-` 前缀并与后端联调对齐。
- 环境/域名只经 `ApiConfig`，不硬编码 URL。
- 解析配置只用 `NetworkModule.json` 一处，DTO 加 `@Serializable`。

## 十四、可改进项

- `ApiService.upload` 只发单个 file part（历史遗留），直传场景已由 `FilesApi.uploadToStorage` 的 `@PartMap` 版本替代，可评估收敛。
- `TokenProvider` 为内存态，进程重启后需认证层重新写入；可在设计上明确「冷启动前的接口调用应等待会话恢复」。
- `NetworkModule` 为对象单例而非 Hilt 提供；如需按环境热切换或测试替身，可评估迁移到 Hilt Module。
- 401（`40101`）目前由各调用方据 `BizError.isAuthExpired` 处理，可考虑在拦截器层统一触发续期/重登，减少重复。
