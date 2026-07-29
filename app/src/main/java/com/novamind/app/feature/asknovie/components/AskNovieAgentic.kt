package com.novamind.app.feature.asknovie.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.asknovie.ChatBlock
import com.novamind.app.feature.asknovie.QuadrantCell
import com.novamind.app.ui.theme.AppTheme

/**
 * 技能/工具执行状态行（Figma：3×3 点阵 + 文案，如 "using visualise skill"）。
 * [working] 为 true 时点阵做轻微呼吸动画表示进行中。
 */
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
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_skill_dots),
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

/**
 * 内联 2×2 象限图（Figma「visualise」技能产物）。[cells] 顺序为 左上 / 右上 / 左下 / 右下。
 */
@Composable
internal fun QuadrantDiagram(data: ChatBlock.Quadrant, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(data.title, color = TextTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(data.subtitle, color = TextSub, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        // 顶部轴标签
        Text(
            data.topAxis,
            color = TextSub,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp),
        )
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SideAxisLabel(data.leftAxis)
            Box(modifier = Modifier.weight(1f)) {
                QuadrantGrid(data.cells)
            }
            SideAxisLabel(data.rightAxis)
        }

        Spacer(Modifier.height(4.dp))
        // 底部轴标签
        Text(
            data.bottomAxis,
            color = TextSub,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp),
        )
    }
}

/** 竖排轴标签（旋转 90°），固定占位宽度 18dp。 */
@Composable
private fun SideAxisLabel(text: String) {
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        // requiredWidth 让文本在旋转前按完整长度测量（否则会被 18dp 宽约束截断为 "Jun"）。
        Text(
            text,
            color = TextSub,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .requiredWidth(140.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun QuadrantGrid(cells: List<QuadrantCell>) {
    val safe = (cells + List(4) { QuadrantCell("", emptyList()) }).take(4)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, FieldBorder, RoundedCornerShape(8.dp)),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            QuadrantCellView(safe[0], Modifier.weight(1f))
            VLine()
            QuadrantCellView(safe[1], Modifier.weight(1f))
        }
        HLine()
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            QuadrantCellView(safe[2], Modifier.weight(1f))
            VLine()
            QuadrantCellView(safe[3], Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuadrantCellView(cell: QuadrantCell, modifier: Modifier) {
    val fg = if (cell.highlight) QuadrantHighlightText else TextTitle
    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(if (cell.highlight) Modifier.background(QuadrantHighlightBg) else Modifier)
            .padding(10.dp),
    ) {
        cell.badge?.let {
            Text(it, color = QuadrantHighlightText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
        }
        Text(cell.heading, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        cell.lines.forEach { line ->
            Spacer(Modifier.height(2.dp))
            Text(
                line,
                color = if (cell.highlight) QuadrantHighlightText else TextSub,
                fontSize = 9.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
private fun VLine() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(FieldBorder),
    )
}

@Composable
private fun HLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FieldBorder),
    )
}

/** 生成的笔记结果卡片（标题 / 正文 / 日期），可点击打开。 */
@Composable
internal fun NoteResultCard(data: ChatBlock.NoteResult, onClick: () -> Unit = {}) {
    Surface(
        color = Card,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(16.dp),
        ) {
            Text(data.title, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(data.body, color = TextSub, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                data.dateLabel,
                color = NoteDate,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 「Create as a note」行动按钮（深色实心胶囊 + 编辑图标）。 */
@Composable
internal fun CreateNoteCta(onClick: () -> Unit) {
    Surface(color = Dark, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = OnDark),
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_edit_square),
                contentDescription = null,
                tint = OnDark,
                modifier = Modifier.size(16.dp),
            )
            Text("Create as a note", color = OnDark, fontSize = 14.sp)
        }
    }
}

/** 会话末尾的免责声明（花标 + 双行灰字，右对齐）。 */
@Composable
internal fun FooterDisclaimer() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_profile_about_novie),
            contentDescription = null,
            tint = TextTitle,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "AI can make mistakes.\nPlease double-check responses.",
            color = TextSub,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.End,
        )
    }
}

// ─── Preview ───

private val previewQuadrant = ChatBlock.Quadrant(
    title = "First CS hire — seniority × specialization",
    subtitle = "Sarah's framing, visualized",
    topAxis = "Onboarding-focused",
    bottomAxis = "Generalist",
    leftAxis = "Junior",
    rightAxis = "Senior",
    cells = listOf(
        QuadrantCell(
            "Junior + focused",
            listOf("Cheap, narrow coaching cost", "Scope is the role", "— training window is bounded"),
            highlight = true,
            badge = "◆ SARAH'S TARGET",
        ),
        QuadrantCell(
            "Senior + focused",
            listOf("Expensive, low coaching cost", "Senior for a narrow scope", "— may feel small to them"),
        ),
        QuadrantCell(
            "Junior + broad",
            listOf("Cheap but heavy coaching", "Broad CS scope + junior", "≈ 10 hrs/week back on you"),
        ),
        QuadrantCell(
            "Senior + broad",
            listOf("Expensive AND scope creep risk", "Senior generalists", "reshape the role"),
        ),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "AskNovie · Skill status")
@Composable
private fun SkillStatusPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkillStatusRow("using visualise skill")
            SkillStatusRow("Creating notes now..")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "AskNovie · Quadrant diagram")
@Composable
private fun QuadrantPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) { QuadrantDiagram(previewQuadrant) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "AskNovie · Note result card")
@Composable
private fun NoteResultPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            NoteResultCard(
                ChatBlock.NoteResult(
                    "Q3 KPIs",
                    "Discussed Q3 KPIs. John to finalize the report by Thursday.",
                    "AUG 1   10:00AM",
                ),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "AskNovie · CTA + footer")
@Composable
private fun CtaFooterPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CreateNoteCta {}
            FooterDisclaimer()
        }
    }
}
