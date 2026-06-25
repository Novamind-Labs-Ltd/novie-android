package com.novamind.app.feature.create.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.components.ShimmerBlock
import com.novamind.app.ui.components.shimmer
import com.novamind.app.ui.theme.AppTheme

private val CardSoft = Color(0xFFF7F6F2)

/**
 * 笔记编辑页加载 / AI「Polishing」骨架图：标题、正文行用扫光占位，
 * 底部卡片显示三点动画与「Polishing」文案。整页覆盖在内容之上即可。
 */
@Composable
fun NoteEditorSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // ── 顶部：左圆形按钮占位 + 右胶囊占位 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBlock(width = 56.dp, height = 56.dp, shape = CircleShape)
            ShimmerBlock(width = 150.dp, height = 52.dp, shape = RoundedCornerShape(26.dp))
        }

        Spacer(Modifier.height(24.dp))

        // ── 标题占位（两行不等宽） ──
        SkeletonLine(widthFraction = 0.80f, height = 30.dp, corner = 16.dp)
        Spacer(Modifier.height(12.dp))
        SkeletonLine(widthFraction = 0.62f, height = 30.dp, corner = 16.dp)

        Spacer(Modifier.height(28.dp))

        // ── 正文行占位（不等宽，模拟段落） ──
        val lineFractions = listOf(0.96f, 0.96f, 0.93f, 0.58f, 0.96f, 0.96f, 0.93f, 0.52f)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            lineFractions.forEach { f ->
                SkeletonLine(widthFraction = f, height = 14.dp, corner = 7.dp)
            }
        }

        Spacer(Modifier.weight(1f))

        // ── 底部「Polishing」卡片 ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            color = CardSoft,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PolishingDots()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Polishing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorTextTitle,
                )
            }
        }
    }
}

/** 单行正文占位：按父宽度的 [widthFraction] 取宽，圆角扫光。 */
@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp, corner: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .shimmer(),
    )
}

/** 三点循环动画（依次提亮），用于「思考 / 处理中」提示。 */
@Composable
private fun PolishingDots(dotColor: Color = ColorTextTitle) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 200),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alpha)),
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NoteEditorSkeletonPreview() {
    AppTheme {
        NoteEditorSkeleton()
    }
}
