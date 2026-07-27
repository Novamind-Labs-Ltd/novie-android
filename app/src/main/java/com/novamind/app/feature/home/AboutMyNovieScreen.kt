package com.novamind.app.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import com.novamind.app.ui.components.TopBarBackButton
import com.novamind.app.ui.theme.AppTheme

/** 官方链接常量（集中一处，便于统一维护）。 */
private object AboutLinks {
    const val WEBSITE = "https://www.novamind-labs.ai"
    const val PRIVACY = "https://www.novamind-labs.ai/privacy"
    const val TERMS = "https://www.novamind-labs.ai/terms"
}

/**
 * 「About MyNovie」全屏页（无状态，可预览）。
 * 版本信息由调用方传入，链接交互留在 UI 层并通过系统浏览器打开。
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
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        TopBarBackButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 16.dp, top = 22.dp),
        )

        Spacer(Modifier.height(10.dp))

        Image(
            painter = painterResource(R.drawable.about_novie_logo),
            contentDescription = "Novie",
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Your notes and AI assistant.\nAnywhere you work.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = ColorTextSub,
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Version $versionName · Build $versionCode",
            fontSize = 12.sp,
            color = ColorTextHint,
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(Modifier.height(37.dp))

        AboutLinksCard(
            onTerms = { uriHandler.openUri(AboutLinks.TERMS) },
            onPrivacy = { uriHandler.openUri(AboutLinks.PRIVACY) },
            onCheckUpdate = onCheckUpdate,
            onWebsite = { uriHandler.openUri(AboutLinks.WEBSITE) },
        )
    }
}

@Composable
private fun AboutLinksCard(
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    onCheckUpdate: () -> Unit,
    onWebsite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutLinkRow("Terms of Service", onTerms)
            AboutLinkRow("Privacy Policy", onPrivacy)
            AboutLinkRow("Check for updates", onCheckUpdate)
            AboutLinkRow("Official website", onWebsite)
        }
    }
}

@Composable
private fun AboutLinkRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = ColorTextTitle,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ColorMenuIcon,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Home · About MyNovie")
@Composable
private fun AboutMyNovieScreenPreview() {
    AppTheme {
        AboutMyNovieScreen(versionName = "1.0", versionCode = 80, onBack = {})
    }
}
