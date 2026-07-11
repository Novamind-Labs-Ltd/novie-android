package com.novamind.app.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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

/** 删除文件夹二次确认（底部弹层）：Delete（红色实心）/ Cancel（描边）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteFolderDialog(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        DeleteFolderContent(folderName = folderName, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/** 弹层内容（解耦出 ModalBottomSheet 以便预览）。 */
@Composable
private fun DeleteFolderContent(
    folderName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Delete $folderName folder?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "This will permanently delete the $folderName folder.",
            fontSize = 14.sp,
            color = ColorTextSub,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(8.dp))
        // Delete（红色实心胶囊）
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
        // Cancel（描边胶囊）
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

@Preview(showBackground = true, name = "Library · DeleteFolder Content")
@Composable
private fun DeleteFolderContentPreview() {
    AppTheme {
        DeleteFolderContent(folderName = "Work", onConfirm = {}, onDismiss = {})
    }
}
