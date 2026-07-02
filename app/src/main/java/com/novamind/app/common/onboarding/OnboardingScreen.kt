package com.novamind.app.common.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import kotlinx.coroutines.launch

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val Bg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val Accent: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Brand.default.current()
private val OnAccent: Color
    @Composable @ReadOnlyComposable get() = TextColors.Inverse.default.current()
private val DotIdle: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()

private data class Page(val emoji: String, val title: String, val subtitle: String)

private val pages = listOf(
    Page("📝", "记录想法", "图文混排、富文本与列表，随手记下灵感。"),
    Page("🖼️", "图片与文档", "插入图片、拍照、附件，浏览与缩放更顺手。"),
    Page("🎙️", "随时录音", "锁屏也能继续录音，历史随时回放。"),
)

/** 首启引导页：左右滑动浏览，最后一页「开始使用」；任意页可「跳过」。 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { /* 引导期间拦截返回，避免误退出 */ }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Box(modifier = modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // 跳过
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "跳过",
                    fontSize = 14.sp,
                    color = TextSub,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onFinish,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val p = pages[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.emoji, fontSize = 88.sp)
                    Spacer(Modifier.height(28.dp))
                    Text(p.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextTitle)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.subtitle,
                        fontSize = 15.sp,
                        color = TextSub,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            }

            // 指示点
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val selected = i == pagerState.currentPage
                    val color by animateColorAsState(if (selected) Accent else DotIdle, label = "dot")
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }

            // 主按钮：下一步 / 开始使用
            Surface(
                shape = RoundedCornerShape(50),
                color = Accent,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = {
                                if (isLast) onFinish()
                                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isLast) "开始使用" else "下一步",
                        color = OnAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
