package com.novamind.app.feature.create.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.novamind.app.R

/**
 * 顶部操作行：左侧圆形返回按钮，右侧 Share | 撤销 | 重做 胶囊。
 */
@Composable
fun CreateTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：圆形返回按钮
        Surface(shape = CircleShape, color = ColorChipBg, shadowElevation = 2.dp) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // 右：胶囊容器 —— Share | 撤销 | 重做
        Surface(
            shape = RoundedCornerShape(50),
            color = ColorChipBg,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIconBtn(
                    icon = R.drawable.ic_share,
                    contentDescription = "Share",
                    enabled = true,
                    onClick = onShare,
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(ColorDivider)
                )
                TopBarIconBtn(
                    icon = R.drawable.ic_undo,
                    contentDescription = "Undo",
                    enabled = canUndo,
                    onClick = onUndo,
                )
                TopBarIconBtn(
                    icon = R.drawable.ic_redo,
                    contentDescription = "Redo",
                    enabled = canRedo,
                    onClick = onRedo,
                )
            }
        }
    }
}

@Composable
private fun TopBarIconBtn(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            tint = if (enabled) ColorTextTitle else ColorTextHint,
            modifier = Modifier.size(20.dp),
        )
    }
}
