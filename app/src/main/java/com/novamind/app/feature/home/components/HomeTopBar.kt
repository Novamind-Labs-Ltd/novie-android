package com.novamind.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.feature.home.HomeMenuItem
import com.novamind.app.ui.theme.AppTheme
import java.io.File

/** 首页顶栏：头像（点击进个人页）+ 操作胶囊（通知带角标 / 更多菜单）。 */
@Composable
internal fun HomeTopBar(
    onMenuAction: (HomeMenuItem) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    avatarPath: String? = null,
    notificationCount: Int = 0,
    initialMenuExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(initialMenuExpanded) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(ColorAvatarBg)
                .clickable(onClick = onAvatarClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            } else {
                Text(text = "👤", fontSize = 22.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BgActionBar,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── 通知按钮 + 右上角红色数量角标 ──────────────────────
                Box {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification),
                        contentDescription = "Notifications",
                        tint = ColorTextTitle,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNotificationsClick),
                    )
                    if (notificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(ColorBadge),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (notificationCount > 9) "9+" else "$notificationCount",
                                color = ColorOnBadge,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 8.sp,
                            )
                        }
                    }
                }
                // ── 更多按钮 + 底部弹窗菜单 ──────────────────────────────
                Icon(
                    painter = painterResource(id = R.drawable.ic_more),
                    contentDescription = "More",
                    tint = ColorTextTitle,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                )
                if (menuExpanded) {
                    MoreSheet(
                        onDismiss = { menuExpanded = false },
                        onItemClick = {
                            menuExpanded = false
                            onMenuAction(it)
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF0EFEA, name = "Home · Top Bar (With Badge)")
@Composable
private fun HomeTopBarPreview() {
    AppTheme {
        HomeTopBar(
            notificationCount = 12,
            modifier = Modifier.padding(16.dp),
        )
    }
}
