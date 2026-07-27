package com.novamind.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import java.io.File

// ─── 配色：统一引用 ui/colors 设计令牌 ───────────────────────────────────────────
private val BgPage: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val BgCard: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val ColorAvatarBg: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.active.current()
private val ColorTitle: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val ColorIcon: Color @Composable @ReadOnlyComposable get() = IconColors.Default.default.current()
private val ColorDanger: Color @Composable @ReadOnlyComposable get() = TextColors.Error.default.current()
private val BtnBg: Color @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val BtnText: Color @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()

/**
 * Profile 页面（无状态）：底栏「Profile」标签对应的独立页面（Figma profile 设计稿）。
 * 顶部为「Profile」大标题 + 头像，下面按 Settings / Support / About 三组分卡片列出入口，
 * 底部为整宽黑色 Sign out 胶囊。底部导航栏由外层 shell 绘制，本页不负责。
 */
@Composable
fun ProfileScreen(
    name: String,
    email: String,
    avatarPath: String?,
    onEditAvatar: () -> Unit = {},
    onNotificationPreferences: () -> Unit = {},
    onConnectors: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onAccessControls: () -> Unit = {},
    onHelpCentre: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onReportIssue: () -> Unit = {},
    onAbout: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // ── 顶部标题栏：Profile 大标题 + 头像 ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 14.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Profile",
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = ColorTitle,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ColorAvatarBg)
                    .clickable(onClick = onEditAvatar),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarPath != null) {
                    AsyncImage(
                        model = File(avatarPath),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                    )
                } else {
                    Text("👤", fontSize = 18.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 160.dp),
        ) {
            Spacer(Modifier.height(10.dp))

            // ── Settings ────────────────────────────────────────────────────
            SectionHeader("Settings")
            SettingsCard {
                SettingsRow(R.drawable.ic_notification, "Notification preferences", onNotificationPreferences)
                SettingsRow(R.drawable.ic_link, "Connectors", onConnectors)
                SettingsRow(R.drawable.ic_key, "Permissions", onOpenPermissions)
                SettingsRow(R.drawable.ic_toggle_on, "Access controls", onAccessControls)
            }

            // ── Support ─────────────────────────────────────────────────────
            SectionHeader("Support")
            SettingsCard {
                SettingsRow(R.drawable.ic_help, "Help centre", onHelpCentre)
                SettingsRow(R.drawable.ic_chat, "Send feedback", onSendFeedback)
                SettingsRow(R.drawable.ic_warning, "Report an issue", onReportIssue)
            }

            // ── About ───────────────────────────────────────────────────────
            SectionHeader("About")
            SettingsCard {
                SettingsRow(R.drawable.ic_novie_flower, "About MyNovie", onAbout)
                SettingsRow(R.drawable.ic_refresh, "Check for updates", onCheckForUpdates)
                SettingsRow(R.drawable.ic_document, "Privacy policy", onPrivacyPolicy)
            }

            Spacer(Modifier.height(24.dp))

            // ── 底部主操作：整宽黑色胶囊「退出登录」───────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(BtnBg)
                    .clickable(onClick = onLogout)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sign out",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = BtnText,
                )
            }
        }
    }
}

/** 分组标题（Figma header1_upnext）：16sp Bold，左缩进 24。 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = ColorTitle,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp),
    )
}

/** 分组卡片：白底圆角 12 + 轻投影，内部纵向排列若干 [SettingsRow]。 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** 单行入口（Figma context menu）：高 48，图标 24 + 标题 16sp Medium。 */
@Composable
private fun SettingsRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (danger) ColorDanger else ColorIcon,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            fontSize = 16.sp,
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
        )
    }
}
