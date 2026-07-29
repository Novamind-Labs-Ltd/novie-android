package com.novamind.app.feature.asknovie.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.util.LruCache
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.novamind.app.common.log.AppLog
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.ui.theme.AppTheme
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener

private const val PAGE_URL = "https://appassets.androidplatform.net/assets/mermaid/diagram.html"
private const val VIEWER_URL = "https://appassets.androidplatform.net/assets/mermaid/viewer.html"
private const val APP_ASSETS_HOST = "appassets.androidplatform.net"
private const val RESULT_PREFIX = "novie-mermaid:"
private const val MIN_HEIGHT_DP = 160
private const val MAX_HEIGHT_DP = 520
private const val MAX_SOURCE_LENGTH = 10_000
private const val MAX_CACHED_SVG_LENGTH = 400_000
private const val RENDER_TIMEOUT_MS = 10_000L

private data class CachedDiagram(val heightDp: Int, val svg: String?)

private object MermaidRenderCache {
    private val values = object : LruCache<String, CachedDiagram>(1_500_000) {
        override fun sizeOf(key: String, value: CachedDiagram): Int = (value.svg?.length ?: 0) + key.length
    }

    @Synchronized fun get(key: String): CachedDiagram? = values.get(key)

    @Synchronized fun put(key: String, value: CachedDiagram) = values.put(key, value)
}

/** Renders an SSE diagram card locally from Mermaid source. */
@Composable
internal fun MermaidDiagramCard(card: ChatCard.Diagram) {
    val inspectionMode = LocalInspectionMode.current
    val theme = if (isSystemInDarkTheme()) "dark" else "base"
    var retryToken by remember(card.mermaid) { mutableStateOf(0) }
    var renderError by remember(card.mermaid, retryToken) { mutableStateOf<String?>(null) }
    var showSource by remember(card.mermaid) { mutableStateOf(false) }
    var showFullscreen by remember(card.mermaid) { mutableStateOf(false) }
    val cacheKey = remember(card.mermaid, theme) { diagramCacheKey(card.mermaid, theme) }
    var cachedDiagram by remember(cacheKey, retryToken) {
        mutableStateOf(MermaidRenderCache.get(cacheKey))
    }
    var rendered by remember(cacheKey, retryToken) { mutableStateOf(cachedDiagram != null) }
    val validationError = validateMermaidSource(card.mermaid)
    Surface(color = Card, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (card.caption.isNotBlank()) {
                Text(card.caption, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (inspectionMode) {
                DiagramFallback("Diagram preview")
            } else if (validationError != null || renderError != null) {
                DiagramError(
                    message = validationError ?: "The diagram couldn't be rendered.",
                    source = card.mermaid,
                    showSource = showSource,
                    canRetry = validationError == null,
                    onToggleSource = { showSource = !showSource },
                    onRetry = { retryToken++ },
                )
            } else {
                val cached = cachedDiagram
                val cachedSvg = cached?.svg
                if (cachedSvg != null) {
                    // Mermaid mindmap 等图形会用 foreignObject 承载文字；Coil 的 SVG
                    // 解码器会丢弃这部分内容。缓存命中后仍交给本地 WebView 展示 SVG，
                    // 但不重新运行 Mermaid 布局，兼顾文字完整性与稳定高度。
                    key("cached", cacheKey, retryToken) {
                        MermaidWebView(
                            source = card.mermaid,
                            theme = theme,
                            zoomEnabled = false,
                            cachedDiagram = cached,
                            onRendered = { rendered = true },
                            onCached = {},
                            onError = { renderError = it },
                        )
                    }
                } else key(card.mermaid, theme, retryToken) {
                    MermaidWebView(
                        source = card.mermaid,
                        theme = theme,
                        zoomEnabled = false,
                        cachedDiagram = cachedDiagram,
                        onRendered = { rendered = true },
                        onCached = {
                            MermaidRenderCache.put(cacheKey, it)
                            cachedDiagram = it
                        },
                        onError = { renderError = it },
                    )
                }
                if (rendered) {
                    TextButton(onClick = { showFullscreen = true }) { Text("Open full screen") }
                }
            }
        }
    }

    if (showFullscreen) {
        MermaidFullscreenDialog(
            source = card.mermaid,
            theme = theme,
            caption = card.caption,
            cachedDiagram = cachedDiagram,
            onDismiss = { showFullscreen = false },
        )
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun MermaidWebView(
    source: String,
    theme: String,
    zoomEnabled: Boolean,
    cachedDiagram: CachedDiagram?,
    modifier: Modifier = Modifier,
    onRendered: () -> Unit,
    onCached: (CachedDiagram) -> Unit,
    onError: (String) -> Unit,
) {
    var heightDp by remember(source) { mutableStateOf(cachedDiagram?.heightDp ?: MIN_HEIGHT_DP) }
    var webView by remember(source) { mutableStateOf<WebView?>(null) }
    var completed by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        delay(RENDER_TIMEOUT_MS)
        if (!completed) onError("Rendering timed out")
    }

    AndroidView(
        modifier = if (zoomEnabled) modifier else modifier.fillMaxWidth().height(heightDp.dp),
        factory = { context ->
            val pageUrl = if (cachedDiagram?.svg != null) VIEWER_URL else PAGE_URL
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                webView = this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    setSupportZoom(zoomEnabled)
                    builtInZoomControls = zoomEnabled
                    displayZoomControls = false
                    useWideViewPort = zoomEnabled
                    loadWithOverviewMode = zoomEnabled
                }
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val url = request.url
                        return if (
                            url.scheme == "https" &&
                            url.host == APP_ASSETS_HOST &&
                            url.path?.startsWith("/assets/") == true
                        ) {
                            assetLoader.shouldInterceptRequest(url) ?: forbiddenResponse()
                        } else {
                            forbiddenResponse()
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.toString() != pageUrl

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (url == VIEWER_URL && cachedDiagram?.svg != null) {
                            view.evaluateJavascript("showDiagram(${JSONObject.quote(cachedDiagram.svg)})") {
                                completed = true
                                onRendered()
                            }
                        } else if (url == PAGE_URL) {
                            view.evaluateJavascript(
                                "renderDiagram(${JSONObject.quote(source)}, ${JSONObject.quote(theme)})",
                                null,
                            )
                        }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        AppLog.w("MermaidDiagram") { "renderer gone crashed=${detail.didCrash()}" }
                        onError("Renderer process unavailable")
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) {
                        super.onReceivedTitle(view, title)
                        val result = title?.takeIf { it.startsWith(RESULT_PREFIX) }?.let(::decodeResult) ?: return
                        if (result.optBoolean("ok")) {
                            completed = true
                            val renderedHeight = result.optInt("height", MIN_HEIGHT_DP)
                                .coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                            heightDp = renderedHeight
                            view.evaluateJavascript(
                                "(document.querySelector('#diagram svg') || {}).outerHTML || ''",
                            ) { rawValue ->
                                val svg = decodeJavascriptString(rawValue)
                                    ?.takeIf { it.length <= MAX_CACHED_SVG_LENGTH }
                                onCached(CachedDiagram(renderedHeight, svg))
                                onRendered()
                            }
                        } else {
                            val error = result.optString("error").take(200)
                            AppLog.w("MermaidDiagram") { "render failed: $error" }
                            onError(error)
                        }
                    }
                }
                loadUrl(pageUrl)
            }
        },
    )

    DisposableEffect(source) {
        onDispose {
            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClientCompat()
                destroy()
            }
            webView = null
        }
    }
}

@Composable
private fun MermaidFullscreenDialog(
    source: String,
    theme: String,
    caption: String,
    cachedDiagram: CachedDiagram?,
    onDismiss: () -> Unit,
) {
    var error by remember(source) { mutableStateOf<String?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = SheetBg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = caption.ifBlank { "Diagram" },
                        modifier = Modifier.weight(1f),
                        color = TextTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                if (error == null) {
                    MermaidWebView(
                        source = source,
                        theme = theme,
                        zoomEnabled = true,
                        cachedDiagram = cachedDiagram,
                        modifier = Modifier.fillMaxSize(),
                        onRendered = {},
                        onCached = {},
                        onError = { error = it },
                    )
                } else {
                    DiagramFallback("Diagram unavailable")
                }
            }
        }
    }
}

@Composable
private fun DiagramError(
    message: String,
    source: String,
    showSource: Boolean,
    canRetry: Boolean,
    onToggleSource: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, color = TextSub, fontSize = 14.sp)
        Row {
            if (canRetry) TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onToggleSource) { Text(if (showSource) "Hide source" else "View source") }
        }
        if (showSource) {
            SelectionContainer {
                Text(
                    text = source.take(MAX_SOURCE_LENGTH),
                    modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    color = TextSub,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun DiagramFallback(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(MIN_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TextSub, fontSize = 14.sp)
    }
}

private fun decodeResult(title: String): JSONObject? = runCatching {
    val encoded = title.removePrefix(RESULT_PREFIX)
    val json = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    JSONObject(json)
}.getOrNull()

private fun decodeJavascriptString(value: String?): String? = runCatching {
    JSONTokener(value.orEmpty()).nextValue() as? String
}.getOrNull()?.takeIf(String::isNotBlank)

private fun diagramCacheKey(source: String, theme: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$theme:$source".encodeToByteArray())
    return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE)
}

internal fun validateMermaidSource(source: String): String? = when {
    source.isBlank() -> "The diagram source is empty."
    source.length > MAX_SOURCE_LENGTH -> "The diagram is too large to render safely."
    else -> null
}

private fun forbiddenResponse() = WebResourceResponse(
    "text/plain",
    "utf-8",
    403,
    "Forbidden",
    emptyMap(),
    ByteArrayInputStream(ByteArray(0)),
)

@Preview(showBackground = true)
@Composable
private fun MermaidDiagramCardPreview() {
    AppTheme {
        MermaidDiagramCard(
            ChatCard.Diagram(
                diagramType = "flowchart",
                mermaid = "flowchart LR; A[Plan] --> B[Build] --> C[Review]",
                caption = "Project workflow",
            ),
        )
    }
}
