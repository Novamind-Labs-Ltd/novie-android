# App Links 配置说明（app.novamind-labs.ai）

让浏览器输入 `https://app.novamind-labs.ai/...` 直接调起本应用、无选择弹窗。
分为「客户端」和「服务端」两部分，两者都到位 App Links 才会被系统验证通过。

## 一、客户端（已在代码中完成）

- `AndroidManifest.xml` 的 `MainActivity` 新增 `android:autoVerify="true"` 的 VIEW intent-filter，
  绑定 `scheme=https` + `host=app.novamind-labs.ai`，并设 `launchMode="singleTask"`。
- `common/deeplink/DeepLinks.kt`：把进入的 URI 解析为内部 route（当前默认进首页 Home，可扩展）。
- `MainActivity`：`onCreate` / `onNewIntent` 解析 intent → 驱动 `currentRoute`。

## 二、服务端（需手动部署）

### 1. 取签名证书的 SHA256 指纹

**Debug 包**（默认 debug keystore，用于联调）：

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android \
  | grep "SHA256:"
```

**Release 包**（用你正式发布的 keystore）：

```bash
keytool -list -v -keystore /path/to/release.keystore \
  -alias <your-alias> | grep "SHA256:"
```

> 若用 Google Play 应用签名（Play App Signing），用 **Play Console → 设置 → 应用完整性** 里
> “应用签名密钥”的 SHA-256，而不是上传密钥。

### 2. 填入 assetlinks.json

把上一步的指纹（形如 `AB:CD:...:EF`）替换 `assetlinks.json` 里的占位符：

- `REPLACE_WITH_RELEASE_SHA256` → release 指纹
- `REPLACE_WITH_DEBUG_SHA256` → debug 指纹（仅联调需要；正式上线可删掉 debug 那一段）

一个 package 可放多个指纹（上传密钥 + Play 签名密钥都列上最稳妥）。

### 3. 部署到固定路径

文件必须可通过以下地址访问（HTTPS、200、`Content-Type: application/json`、无重定向）：

```
https://app.novamind-labs.ai/.well-known/assetlinks.json
```

### 4. 验证

```bash
# 直接看文件
curl -i https://app.novamind-labs.ai/.well-known/assetlinks.json

# Google 官方校验接口
https://digitalassetlinks.googleapis.com/v1/statements:list?\
source.web.site=https://app.novamind-labs.ai&\
relation=delegate_permission/common.handle_all_urls
```

安装 App 后，查看系统验证状态：

```bash
adb shell pm get-app-links com.novamind.app
# 期望 app.novamind-labs.ai 状态为 "verified"
```

手动测试调起（不依赖验证）：

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "https://app.novamind-labs.ai/home" com.novamind.app
```

## 注意事项

- `assetlinks.json` 改动后，系统不会立即重新验证；可重装 App 或
  `adb shell pm verify-app-links --re-verify com.novamind.app` 触发。
- host 必须和 manifest、assetlinks.json 三处完全一致。
- 网站若有 www / 裸域跳转，确保 `.well-known` 路径本身不被重定向。
- 新增可直达页面：只改 `DeepLinks.resolve()` 的 when 分支，manifest 无需动
  （intent-filter 未限定 path，整 host 都命中）。
