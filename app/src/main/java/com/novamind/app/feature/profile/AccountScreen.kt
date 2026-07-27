package com.novamind.app.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.BorderColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.IconColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.components.TopBarBackButton
import com.novamind.app.ui.theme.AppTheme
import java.io.File

private val AccountPage: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val AccountCard: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()
private val AccountTitle: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val AccountSub: Color @Composable @ReadOnlyComposable get() = TextColors.Primary.secondary.current()
private val AccountButton: Color @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val AccountButtonText: Color @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
private val AccountSuccessBg: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Success.tertiary.current()
private val AccountSuccessBorder: Color @Composable @ReadOnlyComposable get() = BorderColors.Success.secondary.current()
private val AccountSuccessText: Color @Composable @ReadOnlyComposable get() = TextColors.Success.default.current()
private val AccountProgressTrack: Color @Composable @ReadOnlyComposable get() = BackgroundColors.Interactive.disabled.current()
private val AccountProgress: Color @Composable @ReadOnlyComposable get() = ButtonColors.Brand.default.current()

@Composable
fun AccountScreen(
    name: String,
    email: String,
    avatarPath: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AccountPage)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 22.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopBarBackButton(onClick = onBack)
            Text(
                text = "Account",
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = AccountTitle,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountHero(name = name, avatarPath = avatarPath, onEdit = onEdit)
            SectionTitle("Account details")
            AccountDetails(email = email)
            UsageCard()
            WorkspaceRow()
        }
    }
}

@Composable
private fun AccountHero(name: String, avatarPath: String?, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(BackgroundColors.Interactive.active.current()),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("👤", fontSize = 36.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = name.substringBefore(' ').ifBlank { "My account" },
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccountTitle,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "Novie user" }, fontSize = 14.sp, color = AccountTitle)
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .width(1.dp)
                            .height(10.dp)
                            .background(AccountSub)
                    )
                    Text("Member", fontSize = 14.sp, color = AccountTitle)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(AccountButton)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 32.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Edit", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AccountButtonText)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        color = AccountTitle,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

@Composable
private fun AccountDetails(email: String) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AccountDetailRow(
            iconRes = R.drawable.ic_account_email,
            label = email.ifBlank { "Email unavailable" },
        )
        AccountDetailRow(
            iconRes = R.drawable.ic_account_wallet,
            label = "Subscription",
            badge = true,
        )
    }
}

@Composable
private fun AccountDetailRow(iconRes: Int, label: String, badge: Boolean = false) {
    Surface(shape = RoundedCornerShape(12.dp), color = AccountCard, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccountTitle)
            if (badge) ActiveBadge()
            Spacer(Modifier.weight(1f))
            Chevron()
        }
    }
}

@Composable
private fun ActiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccountSuccessBg)
            .border(1.dp, AccountSuccessBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = AccountSuccessText,
            modifier = Modifier.size(16.dp),
        )
        Text("Active", fontSize = 12.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = AccountSuccessText)
    }
}

@Composable
private fun UsageCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AccountCard,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Usage", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AccountTitle)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("75% used", fontSize = 20.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = AccountTitle)
                Text("Resets on 4 July 2026 · 3 days remaining", fontSize = 12.sp, lineHeight = 20.sp, color = AccountSub)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccountProgressTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(18.dp)
                            .background(AccountProgress),
                    )
                }
                Text(
                    "See usage breakdown",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = AccountSub,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.ic_account_workspace), contentDescription = null, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Workspace", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AccountTitle)
            Text("Novamind", fontSize = 12.sp, lineHeight = 20.sp, color = AccountTitle)
        }
        Chevron()
    }
}

@Composable
private fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = IconColors.Default.default.current(),
        modifier = Modifier.size(16.dp),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AccountScreenPreview() {
    AppTheme {
        AccountScreen(
            name = "Jam Liu",
            email = "jerry@novamind-labs.ai",
            avatarPath = null,
            onBack = {},
            onEdit = {},
        )
    }
}
