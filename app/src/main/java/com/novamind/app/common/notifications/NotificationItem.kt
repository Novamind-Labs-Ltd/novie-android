package com.novamind.app.common.notifications

import androidx.compose.runtime.Immutable

@Immutable
data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timeMs: Long,
    val read: Boolean = false,
)

/** 示例通知数据（后续可替换为真实来源） */
val sampleNotifications: List<NotificationItem> = run {
    val now = System.currentTimeMillis()
    listOf(
        NotificationItem("1", "Monthly report shared", "Team project progress tracking is ready to view.", now - 5 * 60_000),
        NotificationItem("2", "Board meeting reminder", "Internal stakeholder alignment starts in 1 hour.", now - 60 * 60_000),
        NotificationItem("3", "New comment on your note", "Jerry commented on “Market research”.", now - 3 * 60 * 60_000, read = true),
        NotificationItem("4", "Backup completed", "Your notes were backed up successfully.", now - 26 * 60 * 60_000, read = true),
    )
}

/** 未读数量 */
fun List<NotificationItem>.unreadCount(): Int = count { !it.read }
