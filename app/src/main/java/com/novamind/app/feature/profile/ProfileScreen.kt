package com.novamind.app.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.io.File

// ─── 配色：统一引用 ui/colors 设计令牌 ───────────────────────────────────────────
private val BgPage: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val BgCard: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorAvatarBg: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()
private val ColorTitle: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorSub: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val ColorIcon: Color @Composable @ReadOnlyComposable get() = IconColors.Default.secondary.current()
private val ColorDanger: Color @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()

/**
 * Profile 页面（无状态）：底栏「Profile」标签对应的独立页面。
 * 头部展示头像/昵称/邮箱，下面是设置与账号入口。
 */
@Composable
fun ProfileScreen(
    name: String,
    email: String,
    avatarPath: String?,
    isGuest: Boolean,
    onEditAvatar: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onAbout: () -> Unit = {},
    onLogout: () -> Unit = {},
    onLogin: () -> Unit = {},
    appVersion: String = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        Text(
            text = "Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = ColorTitle,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
        )

        // ── 头部：头像 + 昵称/邮箱 ──────────────────────────────────────────
        Surface(shape = RoundedCornerShape(16.dp), color = BgCard, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(ColorAvatarBg)
                        .clickable(onClick = onEditAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarPath != null) {
                        AsyncImage(
                            model = File(avatarPath),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                        )
                    } else {
                        Text("👤", fontSize = 30.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = name.ifBlank { "Guest" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTitle,
                    )
                    if (email.isNotBlank()) {
                        Text(text = email, fontSize = 13.sp, color = ColorSub)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 设置/关于 ───────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(16.dp), color = BgCard, shadowElevation = 1.dp) {
            Column {
                ProfileEntry(R.drawable.ic_key, "Permissions", onClick = onOpenPermissions)
                ProfileEntry(R.drawable.ic_info, "About MyNovie", onClick = onAbout)
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── 账号 ────────────────────────────────────────────────────────────
        Surface(shape = RoundedCornerShape(16.dp), color = BgCard, shadowElevation = 1.dp) {
            if (isGuest) {
                ProfileEntry(R.drawable.ic_key, "Log in / Sign up", onClick = onLogin)
            } else {
                ProfileEntry(R.drawable.ic_key, "Sign out", onClick = onLogout, danger = true)
            }
        }

        if (appVersion.isNotBlank()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "MyNovie $appVersion",
                fontSize = 12.sp,
                color = ColorSub,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ProfileEntry(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (danger) ColorDanger else ColorIcon,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (danger) ColorDanger else ColorTitle,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile · Signed in")
@Composable
private fun ProfileScreenPreview() {
    AppTheme {
        ProfileScreen(
            name = "Jam",
            email = "jam@novamind-labs.ai",
            avatarPath = null,
            isGuest = false,
            appVersion = "v1.0.0",
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Profile · Guest")
@Composable
private fun ProfileScreenGuestPreview() {
    AppTheme {
        ProfileScreen(
            name = "",
            email = "",
            avatarPath = null,
            isGuest = true,
        )
    }
}
