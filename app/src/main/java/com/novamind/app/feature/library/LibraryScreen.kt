package com.novamind.app.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.R
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme

private val BgPage = Color(0xFFF4F2EC)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorBorder = Color(0xFFE3E0D8)
private val ColorAccent = Color(0xFF3D7A5A)
private val ColorIconBtn = Color(0xFFFFFFFF)

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onCreateNote: () -> Unit = {},
    onBack: (() -> Unit)? = null,   // 非 null：左上角显示返回键并触发；null：保持现状（侧栏入口）
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        // 顶部工具条：左侧栏 / 搜索 / 更多
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                // 作为子页进入（如首页 See all）：左上角返回键，通用组件（与 Create 等页统一）
                BackButton(onClick = onBack, background = ColorIconBtn, tint = ColorTextTitle)
            } else {
                // 现状：侧栏入口
                TopIconButton(
                    iconRes = R.drawable.ic_panel_left,
                    desc = "Sidebar",
                    shape = RoundedCornerShape(12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopIconButton(R.drawable.ic_search, "Search", shape = CircleShape)
                TopIconButton(R.drawable.ic_more, "More", shape = CircleShape, bg = Color.Transparent)
            }
        }

        Text(
            text = "Library",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        )

        // 分段标签：Recent / Folders
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TabChip(R.drawable.ic_history, "Recent", selected = true)
            TabChip(R.drawable.ic_folder, "Folders", selected = false)
        }

        if (uiState.notes.isEmpty()) {
            EmptyState(onCreateNote = onCreateNote, modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.notes, key = { it.id }) { note ->
                    LibraryNoteCard(note = note)
                }
            }
        }
    }
}

@Composable
private fun TopIconButton(
    iconRes: Int,
    desc: String,
    shape: androidx.compose.ui.graphics.Shape,
    bg: Color = ColorIconBtn,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = ColorTextTitle,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TabChip(iconRes: Int, label: String, selected: Boolean, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier else Modifier.border(1.dp, ColorBorder, RoundedCornerShape(50))
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (selected) ColorTextTitle else ColorTextSub,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) ColorTextTitle else ColorTextSub,
            )
        }
    }
}

@Composable
private fun EmptyState(onCreateNote: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration()
        Spacer(Modifier.height(28.dp))
        Text(
            text = "No notes yet.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTextTitle,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start a new note to organise your projects, tasks, or brainstorming sessions.",
            fontSize = 14.sp,
            color = ColorTextSub,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // Create new note 按钮（黑色胶囊）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF111111))
                .clickable(onClick = onCreateNote)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_create),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text("Create new note", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 空状态插图：叠放的笔记本/卡片 + 装饰圆点（纯 Canvas 绘制，无需图片资源）。 */
@Composable
private fun EmptyIllustration() {
    Canvas(modifier = Modifier.size(width = 176.dp, height = 140.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w * 0.5f, h * 0.5f)

        // 底部柔和阴影
        drawOval(
            color = Color(0x12000000),
            topLeft = Offset(w * 0.18f, h * 0.82f),
            size = Size(w * 0.64f, h * 0.12f),
        )
        // 后面一本（向左倾斜）
        rotate(degrees = -10f, pivot = center) {
            drawRoundRect(
                color = Color(0xFFE7E3D8),
                topLeft = Offset(w * 0.24f, h * 0.20f),
                size = Size(w * 0.46f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
        // 中间白本（轻微右倾）
        rotate(degrees = 5f, pivot = center) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = ColorBorder,
                topLeft = Offset(w * 0.28f, h * 0.24f),
                size = Size(w * 0.44f, h * 0.52f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2f),
            )
        }
        // 前面：绿色书脊 + 白色页
        drawRoundRect(
            color = ColorAccent,
            topLeft = Offset(w * 0.30f, h * 0.40f),
            size = Size(w * 0.09f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = ColorBorder,
            topLeft = Offset(w * 0.39f, h * 0.40f),
            size = Size(w * 0.30f, h * 0.40f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 2f),
        )
        // 装饰圆点
        drawCircle(color = Color(0xFFCDC8BC), radius = w * 0.045f, center = Offset(w * 0.80f, h * 0.34f))
        drawCircle(color = ColorAccent.copy(alpha = 0.35f), radius = w * 0.018f, center = Offset(w * 0.20f, h * 0.30f))
        drawCircle(color = Color(0xFFCDC8BC), radius = w * 0.014f, center = Offset(w * 0.78f, h * 0.66f))
    }
}

@Composable
private fun LibraryNoteCard(note: LibraryNote) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = ColorAccent.copy(alpha = 0.1f),
            ) {
                Text(
                    text = note.tag,
                    fontSize = 10.sp,
                    color = ColorAccent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(note.preview, fontSize = 12.sp, color = ColorTextSub, lineHeight = 17.sp, maxLines = 3)
        }
    }
}

@Composable
fun LibraryRoute(
    onCreateNote: () -> Unit = {},
    onBack: (() -> Unit)? = null,   // 非 null：作为子页进入，左上角为返回键
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(uiState = uiState, onCreateNote = onCreateNote, onBack = onBack, modifier = modifier)
}

private val sampleNotes = listOf(
    LibraryNote("1", "Market research", "Overview of competitors in 2026.", "Research"),
    LibraryNote("2", "Q2 Strategy", "Key initiatives for Q2 growth plan.", "Strategy"),
    LibraryNote("3", "Design system", "Component tokens and guidelines.", "Design"),
    LibraryNote("4", "Team retro", "Sprint retrospective notes.", "Meeting"),
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryEmptyPreview() {
    AppTheme { LibraryScreen(uiState = LibraryUiState(notes = emptyList())) }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryPopulatedPreview() {
    AppTheme { LibraryScreen(uiState = LibraryUiState(notes = sampleNotes)) }
}
