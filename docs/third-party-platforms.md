# 第三方平台接入速查

> 目标：一页看清 App 已接入的第三方 SDK / 云服务——用途、版本、接入位置与配置来源。
> 范围仅限外部平台/云服务，通用三方库（MMKV、Coil、Retrofit/OkHttp 等）不在此列。
> 版本以 `gradle/libs.versions.toml` 为准，改版本后请同步本表。

## 总览

| 平台 | 用途 | SDK / 版本 | 接入位置 | 配置来源 |
|---|---|---|---|---|
| **Firebase Analytics** | 事件埋点 | Firebase BoM 34.14.0 | `util/FirebaseLogUtils` | `google-services.json` |
| **Firebase Crashlytics** | 崩溃 + 非致命异常上报 | BoM 34.14.0 / 插件 3.0.7 | `util/FirebaseLogUtils` | `google-services.json` |
| **Firebase Cloud Messaging (FCM)** | 推送接收 | BoM 34.14.0 | `common/push/FcmService` | `google-services.json` + Manifest |
| **Firebase In-App Messaging** | 应用内活动展示 | BoM 34.14.0 | 无代码（控制台驱动） | `google-services.json` |
| **Sentry** | 异常 / 性能 / 结构化日志 | SDK 8.34.0 / Gradle 插件 6.13.0 | `util/SentryUtils` | Manifest meta-data（DSN） |
| **Auth0** | 登录（OIDC/PKCE）+ 凭证管理 | 3.14.0 | `feature/auth/AuthManager` | `auth0.properties` → BuildConfig |
| **Google 身份授权** | Calendar / Tasks 的 OAuth token | play-services-auth 21.3.0 / -base 18.3.3 | `common/google/GoogleCalendarAuthManager` | 设备 Google 账号 |

初始化集中在 `NovieApplication.onCreate`：`MMKV → ApiConfig → SentryUtils.init`；Firebase 由 `google-services` 插件在进程启动时自动完成，无需手写 init。

---

## Firebase

通过 `com.google.gms.google-services` 插件（4.4.4）接入，版本由 **Firebase BoM 34.14.0** 统一管理，配置文件 `app/google-services.json`（已入库，非 gitignore）。含四项服务：

- **Analytics** — 事件埋点，封装在 `FirebaseLogUtils`。
- **Crashlytics** — 崩溃与非致命异常上报（`recordException` / `log` / `setUserId` / `setCustomKey`），Gradle 插件 `firebase-crashlytics` 3.0.7。debug 构建关闭 mapping 上传（`mappingFileUploadEnabled = false`）以加快构建。
- **Cloud Messaging (FCM)** — `FcmService` 处理 `onMessageReceived` / `onNewToken`；Manifest 注册 `MESSAGING_EVENT` 服务与默认通知渠道/图标/颜色。**待办**：`onNewToken` 目前只记日志，尚未把 token 上报服务端做定向推送。
- **In-App Messaging** — 仅引入 `firebase-inappmessaging-display` 依赖，无业务代码，由 Firebase 控制台活动自动展示（A/B 实验也在控制台配置）。

> 注意：`google-services.json` 当前只登记了 `com.novamind.app.debug` 一个 client。加入 dev/prod flavor 后，`com.novamind.app`、`com.novamind.app.dev`、`com.novamind.app.dev.debug` 均需在 Firebase 控制台补登记，否则对应 variant 构建会报 “No matching client found”。

## Sentry

SDK `sentry-android` **8.34.0**，配合 Gradle 插件 `io.sentry.android.gradle` **6.13.0**（当前硬编码版本，未走 version catalog）。

- 接入：`SentryUtils.init`——关闭 Manifest 自动初始化（`io.sentry.auto-init=false`），改为按环境覆盖 `environment`（dev / st / prod）与 `release`（= `VERSION_NAME`）。
- Gradle 插件：`org=novamind`、`project=android`；开启 source context 上传；Logcat 插桩上报 `WARNING` 及以上。
- DSN 与若干开关（截图、view hierarchy、user-interaction tracing）在 `AndroidManifest.xml` 的 `io.sentry.*` meta-data 配置。
- **现状提示**：`tracesSampleRate`、`profileSessionSampleRate` 均为 0，`logs.isEnabled = false`——即当前基本只上报事件/崩溃，性能 trace、Profiling、Structured Logs 处于关闭状态，需要时在 `SentryUtils.init` 打开。

## Auth0

SDK `auth0` **3.14.0**，封装层 `AuthManager`。

- **登录**：`WebAuthProvider` 走 Universal Login（系统浏览器 + PKCE），scope `openid profile email offline_access`，优先 Chrome Custom Tabs。
- **凭证**：`SecureCredentialsManager` + `SharedPreferencesStorage` 加密落盘（Keystore），支持自动续期；可选生物识别门控（`BiometricPrompt`，`USE_BIOMETRIC` 权限）。
- **配置**：`Client ID / Domain / Scheme / Audience` 来自 `auth0.properties`（**gitignored**，CI/本地各自提供）→ 注入 `BuildConfig`；`manifestPlaceholders` 注册登录回调 intent-filter。

## Google 身份授权（Play Services）

`play-services-auth` **21.3.0** + `play-services-auth-base` **18.3.3**，封装层 `GoogleCalendarAuthManager`。

- 用途：为设备 Google 账号申请 OAuth access token，scope 为 `calendar.readonly` 与 `tasks`。
- 实现：`GoogleAuthUtil.getToken` 取 token；首次需用户同意时抛 `UserRecoverableAuthException`，走系统同意流程后重试。

---

## 密钥 / 配置来源一览

| 平台 | 配置项 | 位置 | 是否入库 |
|---|---|---|---|
| Firebase | `google-services.json` | `app/` | 是 |
| Sentry | DSN + 开关 | `AndroidManifest.xml` meta-data | 是（DSN 为客户端可嵌值） |
| Auth0 | Client ID / Domain / Scheme / Audience | `auth0.properties` | 否（gitignored） |
| Google 授权 | 无独立密钥，依赖设备账号与 OAuth 同意 | — | — |

## 详细客户端配置

以下按平台列出接入所需的**客户端具体配置**：Gradle 依赖/插件、Manifest、BuildConfig、配置文件与运行时选项。密钥类值（Auth0）用占位符，不入库。

### 1. 通用：BuildConfig 字段

`buildConfig = true`（`android.buildFeatures`）开启后，以下字段注入 `com.novamind.app.BuildConfig`，代码可直接读：

| 字段 | 类型 | 来源 | 说明 |
|---|---|---|---|
| `BUILD_TIME` | String | `defaultConfig`（`SimpleDateFormat` 配置期取值） | 展示构建时间（配置期求值，见文末注意事项） |
| `AUTH0_CLIENT_ID` / `AUTH0_DOMAIN` / `AUTH0_SCHEME` / `AUTH0_AUDIENCE` | String | `auth0.properties` | 见 §4 |
| `DEFAULT_ENV` | String | product flavor | dev=`TEST`，prod=`PROD` |
| `ENV_SWITCHABLE` | boolean | product flavor | dev=`true`，prod=`false`（prod 锁死环境） |
| `DEBUG` / `VERSION_NAME` / `VERSION_CODE` / `APPLICATION_ID` | — | AGP 标准 | — |

### 2. Firebase

**Gradle**（`app/build.gradle.kts`）：

```kotlin
plugins {
    alias(libs.plugins.google.services)       // 4.4.4
    alias(libs.plugins.firebase.crashlytics)  // 3.0.7
}
dependencies {
    implementation(platform(libs.firebase.bom))          // BoM 34.14.0
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.inappmessaging.display)
}
```

**配置文件**：`app/google-services.json`（已入库）。当前仅登记 client `com.novamind.app.debug`——加 flavor 后 `com.novamind.app` / `com.novamind.app.dev` / `com.novamind.app.dev.debug` 需在控制台补登记（不同包名的完整配法见 §6）。

**Crashlytics**：debug 构建关闭 mapping 上传：

```kotlin
debug { configure<CrashlyticsExtension> { mappingFileUploadEnabled = false } }
```

**FCM（Manifest）**：

```xml
<service android:name=".common.push.FcmService" android:exported="false">
    <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
</service>
<meta-data android:name="com.google.firebase.messaging.default_notification_channel_id" android:value="fcm_default" />
<meta-data android:name="com.google.firebase.messaging.default_notification_icon"    android:resource="@drawable/ic_notification" />
<meta-data android:name="com.google.firebase.messaging.default_notification_color"   android:resource="@color/notification_color" />
```

**通知渠道**（`common/push/PushChannels`）：`DEFAULT_ID = "fcm_default"`，名称 `Push Notifications`，`IMPORTANCE_HIGH`。需要 `POST_NOTIFICATIONS` 权限（已在 Manifest 声明，运行时申请）。

### 3. Sentry

**Gradle 依赖 + 插件**：

```kotlin
plugins { id("io.sentry.android.gradle") version "6.13.0" }   // 硬编码，未走 catalog
dependencies { implementation(libs.sentry.android) }          // 8.34.0

sentry {
    org.set("novamind")
    projectName.set("android")
    includeSourceContext.set(true)                 // 上传源码，栈帧显示源代码
    tracingInstrumentation { logcat { enabled.set(true); minLevel.set(LogcatLevel.WARNING) } }
}
```

**Manifest meta-data**（`AndroidManifest.xml`）：

| key | value | 含义 |
|---|---|---|
| `io.sentry.dsn` | `https://…@o4510922460758016.ingest.us.sentry.io/4511651077488640` | 项目 DSN（客户端可嵌值） |
| `io.sentry.auto-init` | `false` | 关闭自动初始化，改由 `SentryUtils.init` |
| `io.sentry.traces.user-interaction.enable` | `true` | 用户交互自动面包屑 |
| `io.sentry.attach-screenshot` | `true` | 崩溃附截图（可能含 PII） |
| `io.sentry.attach-view-hierarchy` | `true` | 崩溃附视图层级 |
| `io.sentry.traces.sample-rate` | `1.0` | 见下：被 init 覆盖为 0 |

**运行时覆盖**（`util/SentryUtils.init`，在 `ApiConfig.init` 之后调用）：

```kotlin
options.environment = "dev" | "st" | "prod"     // dev=DEBUG包，prod=PROD环境，其余 st
options.release = BuildConfig.VERSION_NAME
options.tracesSampleRate = 0.0                  // 覆盖 Manifest 的 1.0
options.logs.isEnabled = false                  // Structured Logs 关闭
options.profileLifecycle = ProfileLifecycle.TRACE
options.profileSessionSampleRate = 0.0
```

> 即：Manifest 里 `traces.sample-rate=1.0` 只在 auto-init 生效；因 auto-init 关闭，实际以 init 里的 `0.0` 为准，性能 trace/Profiling/Logs 当前均关闭。

### 4. Auth0

**配置文件** `auth0.properties`（项目根，**gitignored**，CI/本地各自提供）：

```properties
AUTH0_CLIENT_ID=<your-client-id>
AUTH0_DOMAIN=<your-tenant>.auth0.com
AUTH0_SCHEME=<https 或自定义 scheme>
AUTH0_AUDIENCE=<api-audience，可空>
```

**Gradle** 读取并注入（文件缺失时降级空值，保证可编译）：

```kotlin
buildConfigField("String", "AUTH0_CLIENT_ID", "\"${auth0("AUTH0_CLIENT_ID")}\"")
// … DOMAIN / SCHEME / AUDIENCE 同理
manifestPlaceholders["auth0Domain"] = auth0("AUTH0_DOMAIN")
manifestPlaceholders["auth0Scheme"] = auth0("AUTH0_SCHEME", "https")
```

`manifestPlaceholders` 供 Auth0 SDK 经 Manifest 合并自动注入 `RedirectActivity` 的 intent-filter（登录/登出回跳），无需手写 Activity。

**登录参数**（`feature/auth/AuthManager`）：scope `openid profile email offline_access`，附加 `prompt=login`（每次强制显示登录页）；凭证经 `SecureCredentialsManager` + `SharedPreferencesStorage` 加密落盘。

**回调 URL**（需在 Auth0 控制台 Allowed Callback/Logout URLs 白名单登记）：

```
<AUTH0_SCHEME>://<AUTH0_DOMAIN>/android/<applicationId>/callback
```

> `applicationId` 随 flavor/buildType 变化（`com.novamind.app[.dev][.debug]`），每个 variant 的回调 URL 都需在控制台各自登记，否则该 variant 登录会失败。

### 5. Google 身份授权（Play Services）

**Gradle**：

```kotlin
implementation(libs.play.services.auth)       // 21.3.0
implementation(libs.play.services.auth.base)  // 18.3.3
```

**Scope**（`common/google/GoogleCalendarAuthManager`）：

```kotlin
SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
SCOPE_TASKS             = "https://www.googleapis.com/auth/tasks"
OAUTH2_SCOPE            = "oauth2:$SCOPE_CALENDAR_READONLY $SCOPE_TASKS"
```

用 `GoogleAuthUtil.getToken(account, OAUTH2_SCOPE)` 换 access token；首次需同意时抛 `UserRecoverableAuthException`，走系统同意界面后重试。无独立客户端密钥，依赖设备已登录的 Google 账号。

### 6. 多包名（dev/prod flavor）下的 Firebase 配置

加了 flavor 后每个 variant 的 `applicationId` 不同，而 **google-services 插件按包名匹配 `client`**：处理时只取 `client_info/android_client_info/package_name` 等于当前 variant 包名的那个 client；**一个都不匹配就构建报错**。当前包名矩阵：

| variant | applicationId |
|---|---|
| devDebug | `com.novamind.app.dev.debug` |
| devRelease | `com.novamind.app.dev` |
| prodDebug | `com.novamind.app.debug` |
| prodRelease | `com.novamind.app` |

有两种配法，二选一：

**方案 A —— 单 Firebase 项目，多个 App（推荐用于「同一项目、看板内分应用」）**

在同一个 Firebase 项目里，把上面 4 个包名各注册为一个 Android App，下载合并后的 `google-services.json`（含 4 个 `client` 条目）放在 `app/`。所有 variant 都从这一个文件按包名各取所需。改动最小，但 dev/prod 数据在同一项目内（靠不同 App 区分）。

**方案 B —— dev / prod 分别用独立 Firebase 项目（推荐用于「dev 数据完全不进生产」）**

按 flavor 放置 flavor 专属文件，插件会优先用 source set 里的文件、回退到 `app/` 根：

```
app/
    google-services.json                 # 兜底（可选）
    src/dev/google-services.json         # dev 项目：含 com.novamind.app.dev[.debug] 两个 client
    src/prod/google-services.json        # prod 项目：含 com.novamind.app[.debug] 两个 client
```

查找优先级（高 → 低，取第一个存在的）：

```
src/<flavor><BuildType>/  →  src/<buildType>/<flavor>/  →  src/<flavor>/  →  src/<buildType>/  →  app/
```

即构建 `devDebug` 时，`src/dev/google-services.json` 会覆盖根目录的文件。每个 flavor 的文件里仍需含该 flavor 两个 buildType 对应的 client 条目（dev 文件要有 `com.novamind.app.dev` 和 `com.novamind.app.dev.debug`）。此方案把 dev 的 Analytics/Crashlytics/推送与生产彻底隔离，与工程「debug 数据不污染生产看板」的思路一致。

> 注意事项：
> - flavor 目录名区分大小写，须与声明一致的**小写**形式（`dev`/`prod`），插件不会去找首字母大写的目录。
> - 若采用方案 B 且 `src/dev`、`src/prod` 都提供了文件，根目录的 `app/google-services.json` 可省略；保留则仅作未命中时的兜底。
> - 选定后请更新 §2 与「密钥/配置来源一览」，注明当前采用的方案与文件位置。

**其他按包名区分的配置**

- **Auth0 回调 URL**：格式 `<SCHEME>://<DOMAIN>/android/<applicationId>/callback`，每个 variant 各不相同，需在 Auth0 控制台白名单逐个登记（详见 §4）。
- **FileProvider authority**：`${applicationId}.fileprovider`，AGP 自动按包名生成，无需手工干预。

### 注意事项

- **`BUILD_TIME` 与 configuration cache**：该字段在配置期用 `Date()` 求值。一旦开启 configuration cache，值会被冻结为「生成缓存时刻」，复用缓存的构建不刷新。若需真实构建时间，应改用 `ValueSource` 方式在执行期求值。
- **敏感值来源**：Auth0 四项走 `auth0.properties`（不入库）；Firebase 走 `google-services.json`（入库）；Sentry DSN 在 Manifest（DSN 为客户端可嵌值，非机密）。

## 接入配置步骤（按平台）

从零接入或换环境时，按平台照做。每步分「控制台/服务端侧」与「工程侧」；工程侧的字段位置见前面 §2–§6。

### Firebase

控制台侧（[Firebase Console](https://console.firebase.google.com)）：

1. 建/选 Firebase 项目（如需 dev/prod 隔离，见 §6 方案 B，建两个项目）。
2. 在项目里为**每个 variant 的包名**添加 Android App：`com.novamind.app`、`com.novamind.app.debug`、`com.novamind.app.dev`、`com.novamind.app.dev.debug`。
3. 按需开启服务：Analytics、Crashlytics、Cloud Messaging、In-App Messaging。
4. 下载 `google-services.json`。

工程侧：

1. 按 §6 选定方案放置 `google-services.json`（方案 A 放 `app/`；方案 B 放 `app/src/dev/`、`app/src/prod/`）。
2. 确认 `app/build.gradle.kts` 已应用 `google-services` / `firebase-crashlytics` 插件并引入 BoM 与各服务依赖（见 §2）。
3. FCM：确认 `FcmService` 与默认通知渠道 meta-data 就位（见 §2）；`onNewToken` 待接上报服务端。
4. 构建对应 variant 验证：`./gradlew assembleDevDebug`（包名不匹配会在此报错）。

### Sentry

服务端侧（[sentry.io](https://sentry.io)）：

1. 建 organization `novamind`、project `android`（与 `sentry{}` 块的 `org`/`projectName` 一致）。
2. 复制该 project 的 **DSN**。
3. （可选）生成 auth token 供 CI 上传 source map/context（配 `SENTRY_AUTH_TOKEN` 环境变量或 `sentry.properties`）。

工程侧：

1. 把 DSN 填入 `AndroidManifest.xml` 的 `io.sentry.dsn` meta-data（见 §3）。
2. 保持 `io.sentry.auto-init=false`，环境相关项在 `SentryUtils.init` 配置。
3. 采样率按需在 `SentryUtils.init` 调整（当前 trace/logs/profiling 均为 0/关闭）。

### Auth0

控制台侧（Auth0 Dashboard）：

1. 建 **Native** 类型 Application，记下 Domain 与 Client ID。
2. Allowed Callback URLs / Logout URLs 填入**每个 variant** 的回调地址：`<SCHEME>://<DOMAIN>/android/<applicationId>/callback`（applicationId 见 §6 矩阵）。
3. （如调用自有 API）建 API，记下 Identifier 作为 `AUTH0_AUDIENCE`。
4. 确认应用授权的 scope 含 `openid profile email offline_access`。

工程侧：

1. 在项目根建 `auth0.properties`（**勿入库**，已 gitignore），填 `AUTH0_CLIENT_ID/DOMAIN/SCHEME/AUDIENCE`（见 §4）。
2. 无需手写 RedirectActivity —— `manifestPlaceholders` 已注入，SDK 经 Manifest 合并自动注册。
3. 登录/取凭证逻辑走 `AuthManager`，生物识别门控依赖设备已录入指纹/人脸。

### Google 身份授权（Calendar / Tasks）

控制台侧（[Google Cloud Console](https://console.cloud.google.com)，建议与 Firebase 同项目）：

1. 启用 **Google Calendar API** 与 **Google Tasks API**。
2. 配置 OAuth 同意屏幕，添加 scope：`calendar.readonly`、`tasks`（外部用户需走验证/测试用户）。
3. 建 **OAuth 2.0 Client ID（Android 类型）**：填包名 + 签名证书 **SHA-1**。每个 (包名, 证书) 组合各需一条——即 dev/prod × debug/release 密钥的组合都要覆盖。用 `./gradlew signingReport` 取各 variant 的 SHA-1。

工程侧：

1. 无需在 App 内放密钥；`GoogleCalendarAuthManager` 用 `GoogleAuthUtil.getToken` 按 scope 取 token（见 §5）。
2. 首次授权抛 `UserRecoverableAuthException` → 拉起系统同意界面，同意后重试即可。

## 相关文档

- 网络与统一响应：`docs/http-networking-design.md`
- 日志系统（与 Sentry / FCM 上云联动）：`docs/log-system-design.md`
- 日历连接（Google 授权）：`docs/calendar-connection-design.md`
- 用户会话：`docs/user-session-design.md`
