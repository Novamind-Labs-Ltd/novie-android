package com.novamind.app.common.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme
import com.novamind.app.util.TimeUtils

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val BgPage: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val Card: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val TextTitle: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val TextSub: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val Unread: Color
    @Composable @ReadOnlyComposable get() = IconColors.Error.default.current()
private val Accent: Color
    @Composable @ReadOnlyComposable get() = IconColors.Brand.default.current()

/** 通知列表全屏页。点左上角返回或系统返回关闭。 */
@Composable
fun NotificationListScreen(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val hasUnread = notifications.any { !it.read }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // 顶栏：返回 | 标题 | 全部已读
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackButton(onClick = onBack, background = Card, tint = TextTitle)
            Text(
                "Notifications",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextTitle,
                modifier = Modifier.weight(1f),
            )
            // 一键清除未读（全部标记已读）
            if (hasUnread) {
                Text(
                    text = "Mark all read",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = onMarkAllRead,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No notifications yet", fontSize = 14.sp, color = TextSub)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notifications, key = { it.id }) { n -> NotificationRow(n) }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem) {
    Surface(shape = RoundedCornerShape(14.dp), color = Card, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 未读红点
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (n.read) Color.Transparent else Unread),
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    n.title,
                    fontSize = 15.sp,
                    fontWeight = if (n.read) FontWeight.Medium else FontWeight.Bold,
                    color = TextTitle,
                )
                Text(n.message, fontSize = 13.sp, color = TextSub, lineHeight = 18.sp)
                Spacer(Modifier.size(2.dp))
                Text(TimeUtils.ago(n.timeMs), fontSize = 11.sp, color = TextSub)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NotificationListPreview() {
    AppTheme { NotificationListScreen(notifications = sampleNotifications, onBack = {}) }
}
