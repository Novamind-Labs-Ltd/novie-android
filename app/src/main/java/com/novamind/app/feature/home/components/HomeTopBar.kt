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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.novamind.app.ui.theme.AppTheme
import java.io.File

/**
 * 首页顶栏（home_final）：头像（36dp，点击进个人页）+「Hi, {name}」问候 + 提醒铃铛（带未读红点）。
 */
@Composable
internal fun HomeTopBar(
    userName: String,
    onNotificationsClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    avatarPath: String? = null,
    notificationCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── 头像 + 问候 ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
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
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                    )
                } else {
                    Text(text = "👤", fontSize = 18.sp)
                }
            }
            Text(
                text = "Hi, ${userName.ifBlank { "there" }}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
            )
        }

        // ── 提醒铃铛 + 未读红点 ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotificationsClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification),
                    contentDescription = "Notifications",
                    tint = ColorTextTitle,
                    modifier = Modifier.size(24.dp),
                )
                if (notificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-5).dp, y = 1.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ColorBadge),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Top Bar (badge)")
@Composable
private fun HomeTopBarPreview() {
    AppTheme {
        HomeTopBar(
            userName = "Jam",
            notificationCount = 3,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Top Bar (no badge)")
@Composable
private fun HomeTopBarNoBadgePreview() {
    AppTheme {
        HomeTopBar(
            userName = "Jam",
            notificationCount = 0,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Top Bar (guest / dot)")
@Composable
private fun HomeTopBarGuestPreview() {
    AppTheme {
        HomeTopBar(
            userName = "",
            notificationCount = 15,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1A1A, name = "Home · Top Bar (Dark)")
@Composable
private fun HomeTopBarDarkPreview() {
    AppTheme(darkTheme = true) {
        HomeTopBar(
            userName = "Jam",
            notificationCount = 3,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
