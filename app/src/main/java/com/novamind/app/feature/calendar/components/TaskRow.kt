package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * 任务行（Figma 1032-43550）：始终为圆角卡片 + 柔和阴影——未完成白底（interactive/default），
 * 已完成 #fcfaf6（interactive/tertiary）。左侧为 40 勾选圈：未完成为黑色描边空圈，已完成为
 * slate #596571 实心圈 + 白色对勾。标题 14 SemiBold（已完成置灰 + 删除线），描述 12 次要色。
 *
 * **点击勾选圈切换完成/未完成**（[onToggleComplete]）；点击卡片其余区域进入编辑（[onClick]）。
 */
@Composable
internal fun TaskRow(
    task: CalendarTask,
    onToggleComplete: (CalendarTask) -> Unit,
    onClick: (CalendarTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    TodoCardRow(
        done = task.isCompleted,
        title = task.title,
        subtitle = task.notes,
        onToggle = { onToggleComplete(task) },
        onClick = { onClick(task) },
        modifier = modifier,
    )
}

/** To-do 卡片行本体（Figma 1032-43550）。 */
@Composable
private fun TodoCardRow(
    done: Boolean,
    title: String,
    subtitle: String?,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSubtitle = !subtitle.isNullOrBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) TodoDoneCardBg else ColorSurface)
            .clickable(onClick = onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = if (hasSubtitle) Alignment.Top else Alignment.CenterVertically,
    ) {
        // 勾选圈：点击切换完成/未完成。未完成=黑色描边空圈；已完成=slate 实心 + 白色对勾
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(if (hasSubtitle) Modifier.offset(y = 4.dp) else Modifier)
                .clip(CircleShape)
                .then(
                    if (done) Modifier.background(TodoCheckedBg)
                    else Modifier.border(1.dp, ColorBorderFocus, CircleShape),
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = ColorOnPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (done) ColorTextSub else ColorTextTitle,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = ColorTextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Calendar · Task row")
@Composable
private fun TaskRowPreview() {
    val day = LocalDate.now()
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TaskRow(
                task = CalendarTask(
                    "t1",
                    "list1",
                    "Submit expense report",
                    day,
                    isCompleted = false,
                    notes = "Include receipts"
                ),
                onToggleComplete = {},
                onClick = {},
            )
            TaskRow(
                task = CalendarTask(
                    "t2",
                    "list1",
                    "Reply to Alice",
                    day,
                    isCompleted = true,
                    notes = "description"
                ),
                onToggleComplete = {},
                onClick = {},
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFF3F1EB,
    name = "Calendar · Task row without description",
)
@Composable
private fun TodoCardRowWithoutDescriptionPreview() {
    AppTheme {
        TodoCardRow(
            done = false,
            title = "Submit expense report",
            subtitle = null,
            onToggle = {},
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
