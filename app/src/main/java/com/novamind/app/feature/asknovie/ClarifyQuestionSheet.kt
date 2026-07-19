package com.novamind.app.feature.asknovie

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.FieldBorder
import com.novamind.app.feature.asknovie.components.SendButton
import com.novamind.app.feature.asknovie.components.SheetBg
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TitleColor
import com.novamind.app.ui.theme.AppTheme

/**
 * 澄清问题底部弹窗（agentic：助手需要更多信息时弹出）。
 * 编号选项直接选择，或在「Something else…」输入自定义答案后发送。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClarifyQuestionSheet(
    question: String,
    options: List<String>,
    onSelect: (Int, String) -> Unit,
    onSubmitOther: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        ClarifyContent(
            question = question,
            options = options,
            onSelect = onSelect,
            onSubmitOther = onSubmitOther,
            onClose = onDismiss,
        )
    }
}

@Composable
private fun ClarifyContent(
    question: String,
    options: List<String>,
    onSelect: (Int, String) -> Unit,
    onSubmitOther: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var other by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question,
                color = TitleColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 26.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = TitleColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        options.forEachIndexed { i, opt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onSelect(i, opt) },
                    )
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberBadge(i + 1)
                Spacer(Modifier.size(14.dp))
                Text(opt, color = TitleColor, fontSize = 15.sp)
            }
            if (i < options.lastIndex) {
                HorizontalDivider(color = FieldBorder, thickness = 1.dp)
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = FieldBorder, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))

        // 「Something else…」自定义答案 + 发送
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_square),
                contentDescription = null,
                tint = SubColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(12.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (other.isEmpty()) {
                    Text("Something else…", color = SubColor, fontSize = 15.sp)
                }
                BasicTextField(
                    value = other,
                    onValueChange = { other = it },
                    textStyle = TextStyle(color = TitleColor, fontSize = 15.sp),
                    cursorBrush = SolidColor(TitleColor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.size(8.dp))
            SendButton(onClick = { if (other.isNotBlank()) onSubmitOther(other.trim()) })
        }
    }
}

/** 编号徽标：描边圆角方块内居中数字。 */
@Composable
private fun NumberBadge(n: Int) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, FieldBorder, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("$n", color = TitleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "AskNovie · Clarify sheet")
@Composable
private fun ClarifyContentPreview() {
    AppTheme {
        ClarifyContent(
            question = "What does CS look like at Nova today?",
            options = listOf("Mostly onboarding", "Ongoing account work", "Reactive support", "Mix"),
            onSelect = { _, _ -> },
            onSubmitOther = {},
            onClose = {},
        )
    }
}
