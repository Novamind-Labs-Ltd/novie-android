package com.novamind.app.common.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.novamind.app.R

private val Bar = Color(0xFFF6F6F4)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)

/**
 * 通用 WebView 容器（全屏页）：加载并浏览 [url]。
 * - 顶栏：返回（先走网页历史，无历史再 [onBack] 关闭）、标题/域名、刷新；
 * - 顶部加载进度条；
 * - 站内链接在容器内打开。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var progress by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // 站内链接继续在容器内加载
                    return false
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    canGoBack = view.canGoBack()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, t: String?) {
                    title = t.orEmpty()
                }
            }
            loadUrl(url)
        }
    }

    // 返回：优先走网页历史，无历史则关闭页面
    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Bar)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BarButton(R.drawable.ic_arrow_back, "Back") {
                if (webView.canGoBack()) webView.goBack() else onBack()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifEmpty { "加载中…" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hostOf(url),
                    fontSize = 11.sp,
                    color = TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BarButton(R.drawable.ic_refresh, "Refresh") { webView.reload() }
        }

        // 进度条（加载中显示）
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
            )
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BarButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = desc,
            tint = TextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun hostOf(url: String): String = runCatching {
    android.net.Uri.parse(url).host ?: url
}.getOrDefault(url)
