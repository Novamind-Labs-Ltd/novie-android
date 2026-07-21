package com.novamind.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novamind.app.R
import com.novamind.app.data.tasks.CalendarTask
import com.novamind.app.ui.theme.AppTheme
import java.time.LocalDate

/**
 * 任务行（Figma 959-61626）：未完成为白色卡片 + slate 圆形清单图标；已完成改为无卡片底、
 * 灰色描边圆形 + done-all 双勾图标，标题置灰 + 删除线。
 * 未完成支持**右滑完成**（StartToEnd），点击进入编辑；滑动只触发 [onComplete] 后回弹，
 * 不真正移除条目——完成态由状态更新驱动。
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
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        backgroundContent = {
            // 右滑露出的背景：绿色 + 勾选图标，示意「完成」
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TodoIcon.copy(alpha = 0.18f))
                    .padding(horizontal = 20.dp),
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
        AgendaRow(
            done = task.isCompleted,
            iconPainter = painterResource(
                if (task.isCompleted) R.drawable.ic_done_all else R.drawable.ic_list_todo,
            ),
            iconTint = if (task.isCompleted) ColorTextSub else ColorTextTitle,
            circleBg = TodoCircleBg,
            title = task.title,
            subtitle = task.notes,
            strikeThroughWhenDone = true,
            onClick = { onClick(task) },
        )
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
                task = CalendarTask("t1", "list1", "Submit expense report", day, isCompleted = false, notes = "Include receipts"),
                onComplete = {},
                onClick = {},
            )
            TaskRow(
                task = CalendarTask("t2", "list1", "Reply to Alice", day, isCompleted = true, notes = "description"),
                onComplete = {},
                onClick = {},
            )
        }
    }
}
