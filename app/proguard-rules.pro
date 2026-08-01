# ProGuard / R8 规则（按本项目实际依赖整理）
#
# 说明：Debug / Release 均开启代码压缩与混淆，下列规则用于保留必要类与成员。
# Room / Coil / OkHttp / Retrofit / Firebase 等大多自带 consumer rules，
# 这里只补充本项目特有、或需要显式保留的部分。

# ─── 通用属性 ────────────────────────────────────────────────────────────────
# 保留注解、泛型签名、内部类、异常签名：序列化 / Retrofit / 反射依赖它们
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
# 崩溃栈可读（Crashlytics）：保留源文件名与行号
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ─── WebView JS Bridge ───────────────────────────────────────────────────────
# 暴露给 JS 的方法不能被混淆/裁剪（window.__novieBridge__.postMessage）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.novamind.app.common.web.bridge.NovieBridgeInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# ─── kotlinx.serialization ───────────────────────────────────────────────────
# 官方推荐 R8 规则：保留 @Serializable 类的合成 serializer 与 Companion/INSTANCE
-dontnote kotlinx.serialization.**
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# 本项目网络 DTO（@Serializable，如 MeDto 等）所在包，连同生成的 $$serializer 一并保留
-keep,includedescriptorclasses class com.novamind.app.common.net.**$$serializer { *; }
-keepclassmembers class com.novamind.app.common.net.** {
    *** Companion;
}
-keepclasseswithmembers class com.novamind.app.common.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── Retrofit / OkHttp ───────────────────────────────────────────────────────
# 保留 Retrofit 接口（方法注解需配合上面的 *Annotation* 属性）
-keep,allowobfuscation interface com.novamind.app.common.net.ApiService
-keep,allowobfuscation interface com.novamind.app.common.net.AuthApi
# Retrofit/OkHttp/Okio 自带 consumer rules，这里仅消除可选依赖的告警
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ─── MMKV（Tencent，含 native 方法，必须整体保留） ──────────────────────────
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# ─── Auth0（凭证管理 / JWT 解析有反射，保守保留） ───────────────────────────
-keep class com.auth0.android.** { *; }
-dontwarn com.auth0.android.**

# ─── Room / Coil / Firebase ──────────────────────────────────────────────────
# 三者均自带 consumer rules：实体、ImageLoader、Crashlytics/FCM 一般无需额外配置。
# Crashlytics 的可读栈已由上面的 SourceFile/LineNumberTable 覆盖。

# ─── Kotlin 协程 ─────────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
