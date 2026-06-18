import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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
        release {
            isMinifyEnabled = false
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

// Room schema 输出目录（配合 exportSchema = true 使用）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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
    // Auth0 认证（Universal Login + 凭证管理）
    implementation(libs.auth0)
    // 网络：OkHttp + Retrofit + kotlinx.serialization
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)
    // 日志拦截器仅打进 Debug 包（抓包联调用）
    debugImplementation(libs.okhttp.logging)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}