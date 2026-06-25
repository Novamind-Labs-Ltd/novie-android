import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// 读取 gitignored 的 auth0.properties（不存在时用空值降级，保证 CI/沙箱可编译）
val auth0Props = Properties().apply {
    val f = rootProject.file("auth0.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun auth0(key: String, default: String = ""): String = auth0Props.getProperty(key, default)

android {
    namespace = "com.novamind.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.novamind.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // debug 独立包名：与 release 同机共存，且在 Firebase 注册为单独的 App
            // （数据进 com.novamind.app.debug，不污染生产看板）。
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // debug 无混淆，跳过 mapping 上传以加快构建
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        release {
            isMinifyEnabled = true
            shrinkResources = true   // 与代码压缩配套：移除未引用资源（需 minify 同时开启）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 供 Debug 工具箱展示构建信息
    defaultConfig {
        buildConfigField("String", "BUILD_TIME", "\"" + SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()) + "\"")
        buildConfigField("String", "GIT_SHA", "\"" + gitSha() + "\"")

        // Auth0 配置（来自 auth0.properties，注入 BuildConfig 供代码读取）
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"${auth0("AUTH0_CLIENT_ID")}\"")
        buildConfigField("String", "AUTH0_DOMAIN", "\"${auth0("AUTH0_DOMAIN")}\"")
        buildConfigField("String", "AUTH0_SCHEME", "\"${auth0("AUTH0_SCHEME", "https")}\"")
        buildConfigField("String", "AUTH0_AUDIENCE", "\"${auth0("AUTH0_AUDIENCE")}\"")

        // 供 Auth0 库注册回调 intent-filter（登录/登出后浏览器跳回 App）
        manifestPlaceholders["auth0Domain"] = auth0("AUTH0_DOMAIN")
        manifestPlaceholders["auth0Scheme"] = auth0("AUTH0_SCHEME", "https")
    }
}

// 取当前 git 短 SHA，失败返回 unknown（沙箱/无 git 时安全降级）
fun gitSha(): String = try {
    val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir).redirectErrorStream(true).start()
    p.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
} catch (_: Exception) {
    "unknown"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 进程级生命周期：App 整体前后台，用于生物识别超时上锁
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // 图片加载
    implementation(libs.coil.compose)
    // 键值存储：MMKV（封装在 common/storage/KeyValueStore，逐步替换 SharedPreferences）
    implementation(libs.mmkv)
    // Markdown 渲染（Debug Markdown 阅读器）
    implementation(libs.markdown.renderer.m3)
    // Auth0 认证（Universal Login + 凭证管理）
    implementation(libs.auth0)
    // 指纹/生物识别（SecureCredentialsManager 生物识别门控；fragment 提供 FragmentActivity）
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    // 网络：OkHttp + Retrofit + kotlinx.serialization
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    // 日志拦截器仅打进 Debug 包（抓包联调用）
    debugImplementation(libs.okhttp.logging)
    // Firebase（BoM 统一管理版本）：Analytics + Crashlytics + Cloud Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    // In-App Messaging（自动展示控制台活动；A/B 实验在控制台对其直接配置）
    implementation(libs.firebase.inappmessaging.display)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}