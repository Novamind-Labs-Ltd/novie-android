package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.novamind.app.feature.asknovie.components.DisabledBtnBg
import com.novamind.app.feature.asknovie.components.FieldBorder
import com.novamind.app.feature.asknovie.components.OnSendGreen
import com.novamind.app.feature.asknovie.components.SendGreen
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TitleColor
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.feature.asknovie.data.OptionItem
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/** PR #33 `options` Card：按 Figma 1389:46324 以底部弹层展示。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsCardSheet(
    card: ChatCard.Options,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BackgroundColors.Interactive.default.current(),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        dragHandle = null,
    ) {
        OptionsSheetContent(card = card, onSubmit = onSubmit, onClose = onDismiss)
    }
}

/** 保留脚本化 agentic 演示入口，视觉与真实 SSE options Card 一致。 */
@Composable
fun ClarifyQuestionSheet(
    question: String,
    options: List<String>,
    onSelect: (Int, String) -> Unit,
    onSubmitOther: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionsCardSheet(
        card = ChatCard.Options(
            prompt = question,
            items = options.mapIndexed { index, label -> OptionItem(index.toString(), label) },
            allowFreeText = true,
            select = "one",
        ),
        onSubmit = { answer ->
            val index = options.indexOf(answer)
            if (index >= 0) onSelect(index, answer) else onSubmitOther(answer)
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun OptionsSheetContent(
    card: ChatCard.Options,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMany = card.select == "many"
    var selectedIds by remember(card) { mutableStateOf(emptySet<String>()) }
    var other by remember(card) { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.prompt,
                color = TitleColor,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Close",
                tint = TitleColor,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onClose,
                    ),
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            card.items.forEachIndexed { index, item ->
                val itemId = item.id.ifBlank { index.toString() }
                val selected = itemId in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                        ) {
                            if (isMany) {
                                selectedIds = if (selected) selectedIds - itemId else selectedIds + itemId
                            } else {
                                onSubmit(item.label)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (selected) SendGreen
                                else BackgroundColors.Scenario.fern.current(),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (selected) "✓" else "${index + 1}",
                            color = if (selected) OnSendGreen else TitleColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            item.label,
                            color = TitleColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (item.description.isNotBlank()) {
                            Text(item.description, color = TitleColor, fontSize = 15.sp)
                        }
                    }
                }
                HorizontalDivider(color = FieldBorder, thickness = 1.dp)
                if (index < card.items.lastIndex) Spacer(Modifier.height(4.dp))
            }

            if (card.allowFreeText) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_square),
                        contentDescription = null,
                        tint = TitleColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (other.isEmpty()) Text("Something else…", color = SubColor, fontSize = 16.sp)
                        BasicTextField(
                            value = other,
                            onValueChange = { other = it },
                            textStyle = TextStyle(color = TitleColor, fontSize = 16.sp),
                            cursorBrush = SolidColor(TitleColor),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    val labels = card.items.filterIndexed { index, item ->
                        item.id.ifBlank { index.toString() } in selectedIds
                    }.map(OptionItem::label)
                    val answer = (labels + listOfNotNull(other.trim().takeIf(String::isNotEmpty)))
                        .joinToString(", ")
                    SheetSendButton(enabled = answer.isNotBlank()) { onSubmit(answer) }
                }
            } else if (isMany) {
                Spacer(Modifier.height(12.dp))
                val answer = card.items.filterIndexed { index, item ->
                    item.id.ifBlank { index.toString() } in selectedIds
                }.joinToString(", ") { it.label }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SheetSendButton(enabled = answer.isNotBlank()) { onSubmit(answer) }
                }
            }
        }
    }
}

@Composable
private fun SheetSendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) SendGreen else DisabledBtnBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_up),
            contentDescription = "Send",
            tint = OnSendGreen,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun OptionsSheetContentPreview() {
    AppTheme {
        OptionsSheetContent(
            card = ChatCard.Options(
                prompt = "Example question here",
                items = (1..4).map {
                    OptionItem("${it - 1}", "Option $it title", "Description shows here.")
                },
                allowFreeText = true,
                select = "one",
            ),
            onSubmit = {},
            onClose = {},
        )
    }
}
