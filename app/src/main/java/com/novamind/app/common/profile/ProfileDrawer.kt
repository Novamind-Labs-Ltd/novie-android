package com.novamind.app.common.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import java.io.File

private val Bg = Color(0xFFFBFAF7)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Divider = Color(0xFFE8E6E0)

/** 个人中心抽屉内容。点头像或「更换头像」可更换。 */
@Composable
fun ProfileDrawerContent(
    avatarPath: String?,
    name: String,
    email: String,
    onChangeAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.fillMaxWidth(0.75f),   // 抽屉宽度 = 屏幕 75%
        drawerContainerColor = Bg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(20.dp),
        ) {
            // 头像（点击更换）
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0C8B8))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onChangeAvatar,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarPath != null) {
                    AsyncImage(
                        model = File(avatarPath),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(CircleShape),
                    )
                } else {
                    Text("👤", fontSize = 34.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextTitle)
            Text(email, fontSize = 13.sp, color = TextSub)

            Spacer(Modifier.height(8.dp))
            Text(
                text = "更换头像",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onChangeAvatar,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
            Spacer(Modifier.height(8.dp))

            // 菜单项（示例）
            ProfileMenuItem(R.drawable.ic_nav_library, "我的收藏")
            ProfileMenuItem(R.drawable.ic_nav_calendar, "日程")
            ProfileMenuItem(R.drawable.ic_notification, "通知设置")
            ProfileMenuItem(R.drawable.ic_more, "设置")
        }
    }
}

@Composable
private fun ProfileMenuItem(iconRes: Int, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = TextSub,
            modifier = Modifier.size(20.dp),
        )
        Text(label, fontSize = 15.sp, color = TextTitle)
    }
}
