package com.novamind.app.feature.create.components
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.BackgroundColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.common.config.FunConfig
import com.novamind.app.ui.theme.AppTheme

/**
 * 顶部操作行：左侧圆形返回按钮，右侧 撤销 | 重做 | 更多(···) 胶囊。
 * 更多按钮展开下拉菜单（当前含「分享」，可继续扩展）。
 *
 * [readOnly] = true（回收站只读态）：右侧改为 Restore | Delete 文字胶囊，不展示编辑操作。
 */
@Composable
fun CreateTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onChangeColor: () -> Unit = {},
    onDelete: () -> Unit = {},
    moreEnabled: Boolean = true,   // 「更多(···)」是否可用（新建空笔记禁用；编辑进入始终可用）
    readOnly: Boolean = false,     // 回收站只读态：右侧显示 Restore | Delete
    onRestore: () -> Unit = {},
    onDeleteForever: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左：返回（扁平图标，无圆底，对齐设计稿）
        TopBarIconBtn(
            icon = R.drawable.ic_arrow_back,
            contentDescription = "Back",
            enabled = true,
            onClick = onBack,
        )

        // 只读态（回收站，Figma 879-27503）：右侧扁平图标 恢复 | 彻底删除
        if (readOnly) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIconBtn(
                    icon = R.drawable.ic_restore,
                    contentDescription = "Restore",
                    enabled = true,
                    onClick = onRestore,
                )
                TopBarIconBtn(
                    icon = R.drawable.ic_trash_line,
                    contentDescription = "Delete forever",
                    enabled = true,
                    onClick = onDeleteForever,
                )
            }
            return@Row
        }

        // 右：撤销 | 重做 | 更多(···)（扁平图标，无胶囊底，对齐设计稿）
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                // 更多：展开下拉菜单
                Box {
                    TopBarIconBtn(
                        icon = R.drawable.ic_more,
                        contentDescription = "More",
                        enabled = moreEnabled,
                        onClick = { if (moreEnabled) menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = BackgroundColors.Surface.default.current(),
                        shadowElevation = 8.dp,
                    ) {
                        // 文档分享由 FunConfig 分期控制：未开放时隐藏菜单项。
                        if (FunConfig.DOCUMENT_SHARE_ENABLED) {
                            DropdownMenuItem(
                                text = {
                                    Text("Share", fontSize = 16.sp, color = TextColors.Primary.default.current())
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text("Change color", fontSize = 16.sp, color = TextColors.Primary.default.current())
                            },
                            onClick = {
                                menuExpanded = false
                                onChangeColor()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Delete", fontSize = 16.sp, color = IconColors.Error.default.current())
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
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
            .size(36.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 18.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = contentDescription,
            tint = if (enabled) TextColors.Primary.default.current() else TextColors.Primary.tertiary.current(),
            modifier = Modifier.size(24.dp),
        )
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8)
@Composable
private fun CreateTopBarPreview() {
    AppTheme {
        CreateTopBar(
            canUndo = true,
            canRedo = false,
            onBack = {},
            onShare = {},
            onUndo = {},
            onRedo = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8, name = "Read-only (recycle bin)")
@Composable
private fun CreateTopBarReadOnlyPreview() {
    AppTheme {
        CreateTopBar(
            canUndo = false,
            canRedo = false,
            onBack = {},
            onShare = {},
            onUndo = {},
            onRedo = {},
            readOnly = true,
        )
    }
}
