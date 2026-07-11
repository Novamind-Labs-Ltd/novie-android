package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.pdf.PdfReader
import com.novamind.app.common.pdf.PdfRenderSession
import com.novamind.app.common.pdf.PdfViewerActivity
import com.novamind.app.feature.create.editor.PdfBlock
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PdfBlockView(
    block: PdfBlock,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val targetWidth = context.resources.displayMetrics.widthPixels
    // 内联整合 PDF 阅读器：开一个常驻 session（按需渲染 + LRU 缓存，见 PdfReader）。
    // 块滚出屏幕时(LazyColumn 懒加载)整体被 dispose，关闭 session 释放内存。
    var session by remember(block.path) { mutableStateOf<PdfRenderSession?>(null) }
    var pageCount by remember(block.path) { mutableStateOf(0) }
    var aspect by remember(block.path) { mutableStateOf(0.707f) }
    var loading by remember(block.path) { mutableStateOf(true) }

    LaunchedEffect(block.path) {
        loading = true
        val s = withContext(Dispatchers.IO) { PdfRenderSession.open(File(block.path), targetWidth) }
        if (s != null) {
            session = s
            pageCount = s.pageCount
            aspect = s.firstPageAspect
        }
        loading = false
    }
    // 块离开组合（滚出屏幕 / 删除）时关闭 session
    DisposableEffect(block.path) {
        onDispose { session?.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFFFFF),
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 头部：文件名 + 删除
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_document),
                            contentDescription = null,
                            tint = TextColors.Primary.secondary.current(),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = block.name,
                            fontSize = 13.sp,
                            color = TextColors.Primary.secondary.current(),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false),
                                onClick = onDelete,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = TextColors.Primary.secondary.current(), fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val s = session
                when {
                    loading -> Text("Rendering PDF…", fontSize = 13.sp, color = TextColors.Primary.tertiary.current())
                    s == null -> Text("Unable to render this PDF", fontSize = 13.sp, color = TextColors.Primary.tertiary.current())
                    else -> {
                        // 内联阅读器：宽度撑满，高度按首页宽高比，封顶屏幕 70%
                        val maxH = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val h = (maxWidth / aspect.coerceAtLeast(0.1f)).coerceAtMost(maxH)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(h),
                            ) {
                                PdfReader(
                                    session = s,
                                    pageCount = pageCount,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFE8E7E2)),
                                )
                                // 右上角：进入全屏阅读器
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = false),
                                            onClick = { PdfViewerActivity.start(context, block.path) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_fullscreen),
                                        contentDescription = "Fullscreen reading",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "$pageCount pages total · swipe to turn pages · pinch/double-tap to zoom · top-right for fullscreen",
                            fontSize = 11.sp,
                            color = TextColors.Primary.tertiary.current(),
                        )
                    }
                }
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

// 说明：PDF 预览需真实文件，无法静态渲染。传入不存在的路径时 PdfRenderSession.open
// 返回 null，组件优雅退化为「无法渲染该 PDF」占位态；此 Preview 仅占位，不展示真实页面。
@Preview(showBackground = true, name = "Create · PdfBlockView")
@Composable
private fun PdfBlockViewPreview() {
    AppTheme {
        PdfBlockView(
            block = PdfBlock(path = "/x.pdf", name = "report.pdf"),
            onDelete = {},
        )
    }
}
