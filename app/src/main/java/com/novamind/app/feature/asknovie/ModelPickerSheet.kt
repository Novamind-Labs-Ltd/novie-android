package com.novamind.app.feature.asknovie

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.components.SendGreen
import com.novamind.app.feature.asknovie.components.SheetBg
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TitleColor
import com.novamind.app.ui.theme.AppTheme

/** 可选模型（AskNovie 底部「模型胶囊」）。 */
data class AiModel(val name: String, val subtitle: String)

/** AskNovie 可选模型清单（默认取最新最强的 Claude 系列 + Fable 5）。 */
val ASKNOVIE_MODELS = listOf(
    AiModel("Fable 5", "For your toughest challenges"),
    AiModel("Opus 4.8", "Great for most tasks"),
    AiModel("Sonnet 4.6", "Fast and capable"),
    AiModel("Haiku 4.5", "Fastest, for quick help"),
)

/** 模型选择底部弹窗：点击「Opus 4.8」胶囊弹出，勾选当前模型。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        ModelPickerContent(selected = selected, onSelect = onSelect)
    }
}

/** 弹窗纯内容（便于复用与 @Preview）。 */
@Composable
private fun ModelPickerContent(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        ASKNOVIE_MODELS.forEach { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = { onSelect(model.name) },
                    )
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, color = TitleColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(model.subtitle, color = SubColor, fontSize = 13.sp)
                }
                if (model.name == selected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "Selected",
                        tint = SendGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, name = "AskNovie · Model picker")
@Composable
private fun ModelPickerContentPreview() {
    AppTheme {
        ModelPickerContent(selected = "Opus 4.8", onSelect = {})
    }
}
