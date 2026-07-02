package com.novamind.app.feature.create.folder.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

/** 「创建新文件夹」描边胶囊按钮：无精准匹配时展示，携带待创建名称。 */
@Composable
internal fun CreateFolderButton(name: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, TextDark),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextDark)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Create new folder ‘$name’",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Folder · 创建按钮")
@Composable
private fun CreateFolderButtonPreview() {
    AppTheme {
        CreateFolderButton(name = "Reading list") {}
    }
}
