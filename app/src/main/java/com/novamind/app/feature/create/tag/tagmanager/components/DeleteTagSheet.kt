package com.novamind.app.feature.create.tag.tagmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme

/** 删除标签二次确认（底部弹层）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteTagSheet(
    tagName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        DeleteTagContent(tagName = tagName, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/** 弹层的纯内容（不含 sheet 容器），便于 @Preview。 */
@Composable
private fun DeleteTagContent(
    tagName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Delete “$tagName” tag?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "This removes the tag from all notes.",
            fontSize = 14.sp,
            color = ColorTextSub,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        Box(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(BackgroundColors.Error.default.current())
                .clickable(onClick = onConfirm)
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Delete", color = Palette.white, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, ColorTextTitle, RoundedCornerShape(50))
                .clickable(onClick = onDismiss)
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancel", color = ColorTextTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ModalBottomSheet 为窗口层，静态预览不渲染；预览内容层。
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Tag · Delete confirmation")
@Composable
private fun DeleteTagContentPreview() {
    AppTheme {
        DeleteTagContent(tagName = "Brand Identity", onConfirm = {}, onDismiss = {})
    }
}
