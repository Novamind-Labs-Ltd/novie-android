package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.novamind.app.ui.colors.BorderColors
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
        // 选项卡是强交互流程：遮罩、系统返回和下滑都不关闭，只允许点右上角叉号。
        onDismissRequest = {},
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            // 阻止遮罩/返回键先把 Sheet 切到 Hidden 后仍留在 Composition 拦截触摸。
            confirmValueChange = { it != SheetValue.Hidden },
        ),
        sheetGesturesEnabled = false,
        containerColor = BackgroundColors.Interactive.default.current(),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        dragHandle = null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            OptionsSheetContent(
                card = card,
                onSubmit = onSubmit,
                onClose = onDismiss,
                // Figma 1389:46559：弹层最高约为可用屏高的 70%。
                modifier = Modifier.heightIn(max = maxHeight * 0.7f),
            )
        }
    }
}

/** `offer` 没有独立选项字段，统一转换成 Yes / Not right now 单选卡片。 */
internal fun ChatCard.Offer.asSingleSelectOptions(): ChatCard.Options = ChatCard.Options(
    prompt = label.ifBlank { "Would you like to continue?" },
    items = listOf(
        OptionItem(id = "accept", label = "Yes"),
        OptionItem(id = "decline", label = "Not right now"),
    ),
    allowFreeText = false,
    select = "one",
)

internal fun ChatCard.Offer.acceptAction(): String? = when (kind) {
    "grilling" -> "start_grilling"
    "summary" -> "pull_summary"
    "note" -> "save_note"
    else -> null
}

/** diagram 暂无服务端 action；用可读请求重入对话，避免发送无语义的 "Yes"。 */
internal fun ChatCard.Offer.acceptText(): String? = when (kind) {
    "diagram" -> label.ifBlank { "Create a diagram." }
    else -> null
}

/** 未知 kind 按卡片协议跳过，等待新版本客户端支持。 */
internal fun ChatCard.Offer.isSupported(): Boolean =
    kind == "summary" || kind == "note" || kind == "diagram" || kind == "grilling"

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
    val selectedLabels = card.items.filterIndexed { index, item ->
        item.id.ifBlank { index.toString() } in selectedIds
    }.map(OptionItem::label)
    val answer = (selectedLabels + listOfNotNull(other.trim().takeIf(String::isNotEmpty)))
        .joinToString(", ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.prompt,
                color = TitleColor,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

        Spacer(Modifier.height(24.dp))

        // 标题和底部输入区固定；只有选项列表在超过最大高度时滚动。
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 12.dp),
        ) {
            itemsIndexed(
                items = card.items,
                key = { index, item -> item.id.ifBlank { index.toString() } },
            ) { index, item ->
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
                                when {
                                    selected -> SendGreen
                                    isMany -> BackgroundColors.Interactive.default.current()
                                    else -> BackgroundColors.Scenario.fern.current()
                                },
                            )
                            .then(
                                if (isMany && !selected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = BorderColors.Default.strong.current(),
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = OnSendGreen,
                                modifier = Modifier.size(18.dp),
                            )
                        } else if (!isMany) {
                            Text(
                                text = "${index + 1}",
                                color = TitleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
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
        }

        if (card.allowFreeText || isMany) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.allowFreeText) {
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
                } else {
                    Spacer(Modifier.weight(1f))
                }
                SheetSendButton(enabled = answer.isNotBlank()) { onSubmit(answer) }
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
