package com.novamind.app.feature.asknovie.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/** 工具执行状态行；执行期间点阵以呼吸动画提示进度。 */
@Composable
internal fun SkillStatusRow(label: String, working: Boolean = true) {
    val isCreatingNote = label == "Creating notes now.."
    val transition = rememberInfiniteTransition(label = "skill")
    val alpha by transition.animateFloat(
        initialValue = if (working) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skillAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCreatingNote) 21.dp else 10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_skill_dots),
            contentDescription = null,
            tint = TextTitle.copy(alpha = if (working) alpha else 1f),
            modifier = Modifier.size(if (isCreatingNote) 24.dp else 18.dp),
        )
        Text(
            text = label,
            color = TextTitle,
            fontSize = if (isCreatingNote) 14.sp else 15.sp,
            fontWeight = if (isCreatingNote) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB)
@Composable
private fun SkillStatusPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkillStatusRow("Creating notes now..")
        }
    }
}
