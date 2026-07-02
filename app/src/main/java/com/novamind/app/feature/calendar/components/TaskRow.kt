package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * 任务行：未完成任务支持**右滑完成**（StartToEnd），点击进入编辑。
 * 滑动只触发 [onComplete] 后回弹，不真正移除条目——完成态由状态更新驱动（置灰 + 删除线）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskRow(
    task: CalendarTask,
    onComplete: (CalendarTask) -> Unit,
    onClick: (CalendarTask) -> Unit,
) {
    val currentTask by rememberUpdatedState(task)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && !currentTask.isCompleted) {
                currentOnComplete(currentTask)
            }
            false // 永不真正 dismiss：回弹，由 UiState 变化切换为已完成样式
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !task.isCompleted,
        enableDismissFromEndToStart = false,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
        backgroundContent = {
            // 右滑露出的背景：绿色 + 勾选图标，示意「完成」
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TodoIcon.copy(alpha = 0.18f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = "Complete task",
                    tint = TodoIcon,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        TaskRowContent(task, onClick = { onClick(task) })
    }
}

@Composable
private fun TaskRowContent(task: CalendarTask, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 任务用勾选圈图标与活动区分；已完成置灰 + 删除线。
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = if (task.isCompleted) TodoIcon else ColorTextFaint,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (task.isCompleted) ColorTextFaint else ColorTextTitle,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            )
            task.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = ColorTextSub, maxLines = 1)
            }
        }
        Text("Task", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TodoIcon)
    }
}

@Preview(showBackground = true, name = "Calendar · 任务行")
@Composable
private fun TaskRowPreview() {
    val day = LocalDate.now()
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskRow(
                task = CalendarTask("t1", "list1", "Submit expense report", day, isCompleted = false, notes = "Include receipts"),
                onComplete = {},
                onClick = {},
            )
            TaskRow(
                task = CalendarTask("t2", "list1", "Reply to Alice", day, isCompleted = true, notes = null),
                onComplete = {},
                onClick = {},
            )
        }
    }
}
