package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/** 无底色圆形图标按钮（胶囊内的历史/更多/添加等）。 */
@Composable
internal fun BareIconButton(
    iconRes: Int,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = if (enabled) TextTitle else TextTitle.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 发送按钮：绿色圆形，仅在有输入内容时显示。 */
@Composable
internal fun SendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SendGreen)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = OnSendGreen),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_up),
            contentDescription = "Send",
            tint = OnSendGreen,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 语音按钮（深色圆形）：失焦时显示。 */
@Composable
internal fun MicButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Dark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = OnDark),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = "Voice",
            tint = OnDark,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 停止按钮（深色圆形 + 白色方块）：回复生成中显示，点击取消本次回复。 */
@Composable
internal fun StopButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Dark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = OnDark),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 实心圆角方块表示「停止」
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(OnDark),
        )
    }
}

/** 悬浮「滚到最新」按钮（圆形白底 + 向下箭头）。 */
@Composable
internal fun ScrollToBottomButton(onClick: () -> Unit) {
    Surface(color = Card, shape = CircleShape, shadowElevation = 0.dp) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // 按压高亮跟随圆形（bounded=false → 圆形状态层），半径=按钮半径，半透明
                    indication = ripple(
                        bounded = false,
                        radius = 18.dp,
                        color = TextTitle.copy(alpha = 0.18f),
                    ),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = "Scroll to latest",
                tint = TextTitle,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─── Preview ───

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · Icon Button Collection")
@Composable
private fun AskNovieButtonsPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(R.drawable.ic_history, "History")
            BareIconButton(R.drawable.ic_more, "More", enabled = false)
            SendButton {}
            MicButton {}
            StopButton {}
            ScrollToBottomButton {}
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · BareIconButton")
@Composable
private fun BareIconButtonPreview() {
    AppTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BareIconButton(R.drawable.ic_history, "History")
            BareIconButton(R.drawable.ic_more, "More", enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · SendButton")
@Composable
private fun SendButtonPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(12.dp)) { SendButton {} }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · MicButton")
@Composable
private fun MicButtonPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(12.dp)) { MicButton {} }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · StopButton")
@Composable
private fun StopButtonPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(12.dp)) { StopButton {} }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · ScrollToBottomButton")
@Composable
private fun ScrollToBottomButtonPreview() {
    AppTheme {
        Row(modifier = Modifier.padding(12.dp)) { ScrollToBottomButton {} }
    }
}
