package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R

private val SheetBg = Color(0xFFFBFAF7)
private val TitleColor = Color(0xFF1A1A1A)
private val SubColor = Color(0xFF8A8A8A)
private val ItemColor = Color(0xFF2A2A2A)
private val DarkPill = Color(0xFF1A1A1A)
private val SearchBg = Color(0xFFFFFFFF)

/** 示例历史会话（后续替换为真实数据）。 */
private val sampleChats = listOf(
    "Strategy session",
    "Strategic Objectives & Key Results review",
    "Market Analysis & Competitive landscape",
    "Actionable Initiatives & Resource planning",
)

/**
 * 「Chat history」底部弹窗：搜索框 + 最近会话列表 + 新建会话。
 * 点 AskNovie 顶栏历史按钮弹出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    onDismiss: () -> Unit,
    onNewChat: () -> Unit = {},
    onSelectChat: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) sampleChats
        else sampleChats.filter { it.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // 标题 + New chat
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chat history", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TitleColor)
                Spacer(Modifier.weight(1f))
                NewChatButton(onClick = onNewChat)
            }

            Spacer(Modifier.height(16.dp))

            // 搜索框
            SearchField(query = query, onQueryChange = { query = it })

            Spacer(Modifier.height(20.dp))

            Text("Recent", fontSize = 14.sp, color = SubColor)
            Spacer(Modifier.height(4.dp))

            filtered.forEach { title ->
                ChatRow(title = title, onClick = { onSelectChat(title) })
            }
        }
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DarkPill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_chat),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("New chat", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(color = SearchBg, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = SubColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search chats", color = SubColor, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(color = TitleColor, fontSize = 15.sp),
                    cursorBrush = SolidColor(TitleColor),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChatRow(title: String, onClick: () -> Unit) {
    Text(
        text = title,
        fontSize = 15.sp,
        color = ItemColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
    )
}
