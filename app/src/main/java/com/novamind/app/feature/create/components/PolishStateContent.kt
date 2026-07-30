package com.novamind.app.feature.create.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.ui.theme.novieMarkdownTypography

@Composable
internal fun PolishStatusBanner(completed: Boolean, modifier: Modifier = Modifier) {
    val rotation = if (!completed) {
        val transition = rememberInfiniteTransition(label = "polishSpinner")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "polishSpinnerRotation",
        )
        value
    } else 0f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(BackgroundColors.Info.default3.current())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(if (completed) R.drawable.ic_check else R.drawable.ic_status_loading),
            contentDescription = null,
            tint = TextColors.Success.onSurface.current(),
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
        Text(
            text = if (completed) "Entire note polished." else "Polishing ...",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = TextColors.Success.onSurface.current(),
        )
    }
}

/** Figma 1166:68823 / 1166:68952 的正文骨架：14dp 行高、24dp 行距。 */
@Composable
internal fun PolishBodySkeleton(modifier: Modifier = Modifier) {
    val widths = listOf(1f, 1f, 1f, .54f, 1f, 1f, 1f, .54f, 1f, 1f, 1f, .54f, 1f, 1f, 1f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColors.Page.secondary.current())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        widths.forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(BackgroundColors.Interactive.default.current()),
            )
        }
    }
}

/** 润色成功后的只读预览；按钮悬浮在底部，因此保留足够尾部空间避免正文被遮挡。 */
@Composable
internal fun PolishResultContent(text: String, modifier: Modifier = Modifier) {
    Markdown(
        content = text,
        typography = novieMarkdownTypography(),
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColors.Page.secondary.current())
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 130.dp),
    )
}

@Composable
internal fun PolishDecisionBar(
    onReject: () -> Unit,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColors.Page.secondary.current())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PolishDecisionButton("Reject", filled = false, onClick = onReject, modifier = Modifier.weight(1f))
        PolishDecisionButton("Accept all", filled = true, onClick = onAccept, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PolishDecisionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(100.dp)
    val background = if (filled) TextColors.Primary.default.current() else BackgroundColors.Page.secondary.current()
    val foreground = if (filled) TextColors.Primary.onDark.current() else TextColors.Primary.default.current()
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(background)
            .then(if (filled) Modifier else Modifier.border(BorderStroke(1.dp, BorderColors.Outline.primary.current()), shape))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = foreground)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun PolishStatesPreview() {
    AppTheme {
        Column {
            PolishStatusBanner(completed = false)
            PolishBodySkeleton()
            PolishStatusBanner(completed = true)
            PolishResultContent("Polished content is shown here.\n\nThe user can review it before accepting.")
            PolishDecisionBar(onReject = {}, onAccept = {})
        }
    }
}
