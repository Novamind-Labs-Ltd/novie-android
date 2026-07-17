package com.novamind.app.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.theme.AppTheme

/**
 * 首页「Up next」未授权 Google 日历时的连接卡片：白底圆角卡 + 深色胶囊「Connect」按钮，
 * 复用日历页的连接语义（点击触发登录账户取 token / 授权同意）。
 */
@Composable
internal fun UpNextConnectCard(
    connecting: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = BgCard,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connect Google Calendar",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ColorTextTitle,
            )
            Text(
                text = "Link your account to see today's meetings and tasks here.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = ColorTextSub,
            )
            Surface(
                onClick = onConnect,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(100.dp),
                color = ColorButtonDark,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_calendar),
                        contentDescription = null,
                        tint = ColorOnDark,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (connecting) "Connecting…" else "Connect",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorOnDark,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Up next Connect")
@Composable
private fun UpNextConnectCardPreview() {
    AppTheme {
        UpNextConnectCard(connecting = false, onConnect = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1EB, name = "Home · Up next Connect (connecting)")
@Composable
private fun UpNextConnectCardConnectingPreview() {
    AppTheme {
        UpNextConnectCard(connecting = true, onConnect = {}, modifier = Modifier.padding(16.dp))
    }
}
