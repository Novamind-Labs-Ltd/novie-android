package com.novamind.app.feature.asknovie

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.FieldBg
import com.novamind.app.feature.asknovie.components.FieldBorder
import com.novamind.app.feature.asknovie.components.IconChipBg
import com.novamind.app.feature.asknovie.components.PillButton
import com.novamind.app.feature.asknovie.components.SheetBg
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TextTitle
import com.novamind.app.feature.asknovie.components.TitleColor
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import com.novamind.app.ui.theme.AppTheme

/**
 * 「Create note」底部弹窗（agentic：把助手结论存为笔记）。
 * 预填标题 + 文件夹 / 标签 chip + Create / Cancel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNoteSheet(
    initialTitle: String,
    folderName: String,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember {
        mutableStateOf(TextFieldValue(initialTitle, TextRange(initialTitle.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        snapshotFlow { sheetState.currentValue }.filter { it == SheetValue.Expanded }.first()
        runCatching { focusRequester.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        CreateNoteContent(
            title = title,
            onTitleChange = { title = it },
            folderName = folderName,
            onCreate = { onCreate(title.text.trim()) },
            onCancel = onDismiss,
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun CreateNoteContent(
    title: TextFieldValue,
    onTitleChange: (TextFieldValue) -> Unit,
    folderName: String,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Create note",
                color = TitleColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onCancel,
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

        Spacer(Modifier.height(20.dp))
        Text("Title", color = SubColor, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

        Surface(color = FieldBg, shape = RoundedCornerShape(12.dp)) {
            BasicTextField(
                value = title,
                onValueChange = { onTitleChange(TextFieldValue(it.text, it.selection)) },
                textStyle = TextStyle(color = TitleColor, fontSize = 15.sp),
                cursorBrush = SolidColor(TitleColor),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FieldBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .focusRequester(focusRequester),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetaChip(R.drawable.ic_folder, folderName)
            MetaChip(R.drawable.ic_tag, "Tags")
        }

        Spacer(Modifier.height(24.dp))
        PillButton(text = "Create", filled = true, enabled = title.text.isNotBlank(), onClick = onCreate)
        Spacer(Modifier.height(12.dp))
        PillButton(text = "Cancel", filled = false, enabled = true, onClick = onCancel)
    }
}

/** 元信息 chip（文件夹 / 标签）：浅灰底 + 图标 + 文案。 */
@Composable
private fun MetaChip(iconRes: Int, label: String) {
    Surface(color = IconChipBg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = SubColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(label, color = TextTitle, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "AskNovie · Create note sheet")
@Composable
private fun CreateNoteContentPreview() {
    AppTheme {
        CreateNoteContent(
            title = TextFieldValue("Team meeting 11 Jun 2026"),
            onTitleChange = {},
            folderName = "Team meetings",
            onCreate = {},
            onCancel = {},
        )
    }
}
