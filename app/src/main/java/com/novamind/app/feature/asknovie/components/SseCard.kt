package com.novamind.app.feature.asknovie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.feature.asknovie.data.OptionItem
import com.novamind.app.ui.theme.AppTheme

/** Ask Novie `event: card` 的通用渲染入口。 */
@Composable
internal fun SseCard(
    card: ChatCard,
    onSendText: (String) -> Unit,
) {
    when (card) {
        is ChatCard.Options -> OptionsSseCard(card, onSendText)
        is ChatCard.Offer -> Unit // 由 AskNovieScreen 的单选底部弹层展示。
        is ChatCard.Summary -> ReadOnlyCard(card.title, card.body)
        is ChatCard.Diagram -> MermaidDiagramCard(card)
        is ChatCard.CreateNote -> ReadOnlyCard(card.draftTitle, card.draftContent)
        is ChatCard.Note -> ReadOnlyCard(card.title, "Note saved")
    }
}

@Composable
private fun OptionsSseCard(card: ChatCard.Options, onSendText: (String) -> Unit) {
    val isMany = card.select == "many"
    var selectedIds by remember(card) { mutableStateOf(emptySet<String>()) }
    var freeText by remember(card) { mutableStateOf("") }

    CardSurface {
        if (card.prompt.isNotBlank()) {
            Text(card.prompt, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        card.items.forEachIndexed { index, item ->
            val itemId = item.id.ifBlank { index.toString() }
            val selected = itemId in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) {
                        if (isMany) {
                            selectedIds = if (selected) selectedIds - itemId else selectedIds + itemId
                        } else {
                            onSendText(item.label)
                        }
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) SendGreen else IconChipBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (selected) "✓" else "${index + 1}", color = if (selected) OnSendGreen else TextTitle, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, color = TextTitle, fontSize = 15.sp)
                    if (item.description.isNotBlank()) {
                        Text(item.description, color = TextSub, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        if (card.allowFreeText) {
            BasicTextField(
                value = freeText,
                onValueChange = { freeText = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = TextTitle, fontSize = 15.sp),
                cursorBrush = SolidColor(TextTitle),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FieldBg)
                            .padding(12.dp),
                    ) {
                        if (freeText.isBlank()) Text("Something else…", color = Hint, fontSize = 15.sp)
                        inner()
                    }
                },
            )
        }
        if (isMany || card.allowFreeText) {
            val selectedLabels = card.items.filterIndexed { index, item ->
                item.id.ifBlank { index.toString() } in selectedIds
            }.map(OptionItem::label)
            val response = (selectedLabels + freeText.trim().takeIf(String::isNotEmpty).orEmpty())
                .filter(String::isNotBlank)
                .joinToString(", ")
            ActionButton("Send", enabled = response.isNotBlank()) { onSendText(response) }
        }
    }
}

@Composable
private fun ReadOnlyCard(title: String, body: String) {
    CardSurface {
        if (title.isNotBlank()) Text(title, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (body.isNotBlank()) Text(body, color = TextSub, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun CardSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Dark else DisabledBtnBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = OnDark, fontSize = 14.sp, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun OptionsSseCardPreview() {
    AppTheme {
        SseCard(
            card = ChatCard.Options(
                prompt = "What matters most?",
                items = listOf(
                    OptionItem("0", "Speed", "Ship the first version quickly"),
                    OptionItem("1", "Quality", "Take more time to polish"),
                ),
                allowFreeText = true,
                select = "many",
            ),
            onSendText = {},
        )
    }
}
