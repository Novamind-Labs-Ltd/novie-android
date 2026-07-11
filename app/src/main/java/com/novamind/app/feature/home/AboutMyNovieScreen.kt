package com.novamind.app.feature.home

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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.home.components.BgCard
import com.novamind.app.feature.home.components.BgPage
import com.novamind.app.feature.home.components.ColorMenuIcon
import com.novamind.app.feature.home.components.ColorTextHint
import com.novamind.app.feature.home.components.ColorTextSub
import com.novamind.app.feature.home.components.ColorTextTitle
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.components.BackButton
import com.novamind.app.ui.theme.AppTheme

// 配色统一取 ui/colors 设计系统令牌（见 HomeColors），品牌绿用 Palette.forrest。
// 纯前端静态页：版本号由 Route 从 BuildConfig 传入，其余为固定文案。

/** 官方链接常量（集中一处，便于统一维护）。 */
private object AboutLinks {
    const val WEBSITE = "https://www.novamind-labs.ai"
    const val PRIVACY = "https://www.novamind-labs.ai/privacy"
    const val TERMS = "https://www.novamind-labs.ai/terms"
    const val CONTACT_MAILTO = "mailto:support@novamind-labs.ai"
}

/**
 * 「About MyNovie」全屏页（无状态，可预览）。
 * 首页「更多」菜单 → About MyNovie 进入。
 *
 * @param versionName 版本名（BuildConfig.VERSION_NAME）
 * @param versionCode 构建号（BuildConfig.VERSION_CODE）
 * @param onBack 返回
 * @param onCheckUpdate 检查更新（占位，默认无操作）
 */
@Composable
fun AboutMyNovieScreen(
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
    ) {
        // ── 顶部栏 ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackButton(onClick = onBack, background = BgCard, tint = ColorTextTitle)
            Text("About", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── 品牌介绍 ────────────────────────────────────────────────────
            BrandHero()

            Spacer(Modifier.height(28.dp))

            // ── 版本信息 ────────────────────────────────────────────────────
            SectionLabel("Version info")
            AboutCard {
                AboutRow(
                    iconRes = R.drawable.ic_info,
                    title = "Current version",
                    trailing = "v$versionName (build $versionCode)",
                )
                RowDivider()
                AboutRow(
                    iconRes = R.drawable.ic_refresh,
                    title = "Check for updates",
                    showChevron = true,
                    onClick = onCheckUpdate,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── 法律与链接 ──────────────────────────────────────────────────
            SectionLabel("Legal & links")
            AboutCard {
                AboutRow(
                    iconRes = R.drawable.ic_shield_check,
                    title = "Privacy Policy",
                    showChevron = true,
                    onClick = { uriHandler.openUri(AboutLinks.PRIVACY) },
                )
                RowDivider()
                AboutRow(
                    iconRes = R.drawable.ic_document,
                    title = "Terms of Service",
                    showChevron = true,
                    onClick = { uriHandler.openUri(AboutLinks.TERMS) },
                )
                RowDivider()
                AboutRow(
                    iconRes = R.drawable.ic_link,
                    title = "Official website",
                    showChevron = true,
                    onClick = { uriHandler.openUri(AboutLinks.WEBSITE) },
                )
                RowDivider()
                AboutRow(
                    iconRes = R.drawable.ic_chat,
                    title = "Contact us",
                    showChevron = true,
                    onClick = { uriHandler.openUri(AboutLinks.CONTACT_MAILTO) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── 团队 / 公司 ─────────────────────────────────────────────────
            SectionLabel("Team")
            AboutCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Novamind Labs",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTitle,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "MyNovie is built by Novamind Labs — a team dedicated to making note-taking and thinking more natural with AI.",
                        fontSize = 13.sp,
                        color = ColorTextSub,
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── 版权声明 ────────────────────────────────────────────────────
            Text(
                "© 2026 Novamind Labs. All rights reserved.",
                fontSize = 12.sp,
                color = ColorTextHint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── 品牌头部 ────────────────────────────────────────────────────────────────

@Composable
private fun BrandHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.forrest50),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_brand),
                contentDescription = "MyNovie",
                tint = Palette.forrest500,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "MyNovie",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ColorTextTitle,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Make every note worth revisiting",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.forrest500,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "MyNovie is an AI-powered note and voice assistant that helps you turn ideas, meetings, and " +
                "daily moments into searchable, reviewable knowledge. Capture with less effort, review with more clarity.",
            fontSize = 13.sp,
            color = ColorTextSub,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

// ─── 复用组件 ────────────────────────────────────────────────────────────────

/** 分组小标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = ColorTextHint,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/** 圆角卡片容器。 */
@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

/** 卡片内行：图标 + 标题 +（可选）右侧文案 / 箭头。 */
@Composable
private fun AboutRow(
    iconRes: Int,
    title: String,
    trailing: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            onClick = onClick,
        )
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Palette.forrest50),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Palette.forrest500,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            color = ColorTextTitle,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, fontSize = 13.sp, color = ColorTextSub)
        }
        if (showChevron) {
            Spacer(Modifier.size(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ColorMenuIcon,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 行分隔线。 */
@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .height(0.5.dp)
            .background(Palette.forrest50),
    )
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Home · About MyNovie")
@Composable
private fun AboutMyNovieScreenPreview() {
    AppTheme {
        AboutMyNovieScreen(versionName = "1.0", versionCode = 1, onBack = {})
    }
}
