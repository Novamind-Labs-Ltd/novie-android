package com.novamind.app.feature.asknovie.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.novamind.app.common.log.AppLog
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.ui.theme.AppTheme
import java.io.ByteArrayInputStream
import org.json.JSONObject

private const val PAGE_URL = "https://appassets.androidplatform.net/assets/mermaid/diagram.html"
private const val APP_ASSETS_HOST = "appassets.androidplatform.net"
private const val RESULT_PREFIX = "novie-mermaid:"
private const val MIN_HEIGHT_DP = 160
private const val MAX_HEIGHT_DP = 520

/** Renders an SSE diagram card locally from Mermaid source. */
@Composable
internal fun MermaidDiagramCard(card: ChatCard.Diagram) {
    val inspectionMode = LocalInspectionMode.current
    val theme = if (isSystemInDarkTheme()) "dark" else "base"
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
            } else {
                key(card.mermaid, theme) {
                    MermaidWebView(source = card.mermaid, theme = theme)
                }
            }
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun MermaidWebView(source: String, theme: String) {
    var heightDp by remember(source) { mutableStateOf(MIN_HEIGHT_DP) }
    var renderFailed by remember(source) { mutableStateOf(source.isBlank()) }
    var webView by remember(source) { mutableStateOf<WebView?>(null) }

    if (renderFailed) {
        DiagramFallback("Diagram unavailable")
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
        factory = { context ->
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
                }
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        return if (request.url.host == APP_ASSETS_HOST) {
                            assetLoader.shouldInterceptRequest(request.url)
                        } else {
                            forbiddenResponse()
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.url.toString() != PAGE_URL

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (url == PAGE_URL) {
                            view.evaluateJavascript(
                                "renderDiagram(${JSONObject.quote(source)}, ${JSONObject.quote(theme)})",
                                null,
                            )
                        }
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        AppLog.w("MermaidDiagram") { "renderer gone crashed=${detail.didCrash()}" }
                        renderFailed = true
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView, title: String?) {
                        super.onReceivedTitle(view, title)
                        val result = title?.takeIf { it.startsWith(RESULT_PREFIX) }?.let(::decodeResult) ?: return
                        if (result.optBoolean("ok")) {
                            heightDp = result.optInt("height", MIN_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                        } else {
                            AppLog.w("MermaidDiagram") { "render failed: ${result.optString("error").take(200)}" }
                            renderFailed = true
                        }
                    }
                }
                loadUrl(PAGE_URL)
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
