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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.asknovie.data.ChatCard
import com.novamind.app.feature.asknovie.data.OptionItem
import com.novamind.app.R
import com.novamind.app.feature.asknovie.NoteCardPreview
import com.novamind.app.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ask Novie `event: card` 的通用渲染入口。 */
@Composable
internal fun SseCard(
    card: ChatCard,
    onSendText: (String) -> Unit,
    onSaveSummary: (ChatCard.Summary) -> Unit,
    onSaveNoteDraft: (ChatCard.SaveNote) -> Unit,
    notePreview: NoteCardPreview?,
    onOpenNote: (noteId: String) -> Unit,
) {
    when (card) {
        is ChatCard.Options -> OptionsSseCard(card, onSendText)
        is ChatCard.Offer -> Unit // 由 AskNovieScreen 的单选底部弹层展示。
        is ChatCard.Summary -> SummarySseCard(card, onSave = { onSaveSummary(card) })
        is ChatCard.Diagram -> MermaidDiagramCard(card)
        is ChatCard.CreateNote -> ReadOnlyCard(card.draftTitle, card.draftContent)
        is ChatCard.SaveNote -> SaveNoteSseCard(card, onSaveNoteDraft)
        is ChatCard.Note -> NoteSseCard(card, notePreview, onOpenNote)
    }
}

/** Figma 1514:57724：保存成功后的 Note 概览卡，整卡点击进入笔记详情。 */
@Composable
private fun NoteSseCard(
    card: ChatCard.Note,
    preview: NoteCardPreview?,
    onOpenNote: (noteId: String) -> Unit,
) {
    val title = preview?.title?.takeIf(String::isNotBlank) ?: card.title
    val body = preview?.body.orEmpty()
    val dateLabel = remember(preview?.updatedAt) { preview?.updatedAt?.let(::formatNoteDate) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenNote(card.noteId) },
        color = Card,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        color = TextTitle,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        letterSpacing = (-0.31).sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        color = TextSub,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!dateLabel.isNullOrBlank()) {
                    Text(
                        text = dateLabel,
                        color = NoteDate,
                        fontSize = 12.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun formatNoteDate(value: String): String? = runCatching {
    val time = Instant.parse(value).atZone(ZoneId.systemDefault())
    val date = time.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    val clock = time.format(DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH))
    "$date   $clock"
}.getOrNull()

/** Figma 862:62429：正文下方的 Save as note 操作，不使用 Summary 白色卡片容器。 */
@Composable
private fun SaveNoteSseCard(
    card: ChatCard.SaveNote,
    onSave: (ChatCard.SaveNote) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val displayContent = card.draftContent.ifBlank { card.draftTitle }
        if (displayContent.isNotBlank()) {
            MarkdownContent(content = displayContent, modifier = Modifier.fillMaxWidth())
        }
        if (card.saveable) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Dark)
                    .clickable { onSave(card) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pencil_line),
                        contentDescription = null,
                        tint = OnDark,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Save as note",
                        color = OnDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Figma 1514:57572：结构化 Summary 卡片与保存 Note 入口。 */
@Composable
private fun SummarySseCard(card: ChatCard.Summary, onSave: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Card,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SummaryBadgeBg)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Summary",
                        color = SummaryBadgeText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.title.isNotBlank()) {
                        Text(
                            text = card.title,
                            color = TextTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (card.body.isNotBlank()) {
                        MarkdownContent(content = card.body, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (card.saveable) {
                    HorizontalDivider(color = FieldBorder)
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Dark)
                            .clickable(onClick = onSave)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Save as note",
                            color = OnDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
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
            onSaveSummary = {},
            onSaveNoteDraft = {},
            notePreview = null,
            onOpenNote = {},
        )
    }
}

@Preview(showBackground = true, name = "Summary card")
@Composable
private fun SummarySseCardPreview() {
    AppTheme {
        Box(Modifier.padding(16.dp)) {
            SummarySseCard(
                card = ChatCard.Summary(
                    title = "Nova certification — early shape",
                    body = "A concise summary of the plan and next steps.",
                    saveable = true,
                ),
                onSave = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Save note card")
@Composable
private fun SaveNoteSseCardPreview() {
    AppTheme {
        Box(Modifier.padding(16.dp)) {
            SaveNoteSseCard(
                card = ChatCard.SaveNote(
                    draftTitle = "Nova customer success",
                    draftContent = "A few things to sharpen the picture. What does CS look like at Nova today?",
                ),
                onSave = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Saved note card")
@Composable
private fun NoteSseCardPreview() {
    AppTheme {
        Box(Modifier.padding(16.dp)) {
            NoteSseCard(
                card = ChatCard.Note(noteId = "note-1", title = "Q3 KPIs"),
                preview = NoteCardPreview(
                    title = "Q3 KPIs",
                    body = "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    updatedAt = "2026-08-01T10:00:00Z",
                ),
                onOpenNote = {},
            )
        }
    }
}
