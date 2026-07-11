package com.novamind.app.feature.asknovie

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.feature.asknovie.components.ChatRow
import com.novamind.app.feature.asknovie.components.NewChatButton
import com.novamind.app.feature.asknovie.components.SearchField
import com.novamind.app.feature.asknovie.components.SheetBg
import com.novamind.app.feature.asknovie.components.SubColor
import com.novamind.app.feature.asknovie.components.TitleColor
import com.novamind.app.ui.theme.AppTheme

/**
 * 「Chat history」底部弹窗：搜索框 + 最近会话列表 + 新建会话。
 * 点 AskNovie 顶栏历史按钮弹出。读取已保存会话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    onDismiss: () -> Unit,
    onNewChat: () -> Unit = {},
    onSelectSession: (ChatSession) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    // 打开时加载已保存会话
    val sessions = remember { ChatSessionStore.load(context) }
    val filtered = remember(query, sessions) {
        if (query.isBlank()) sessions
        else sessions.filter { it.title.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        // 固定较高的整体高度（用屏幕高度的固定比例，避免相对约束在拖动时抖动）
        val sheetHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.85f
        ChatHistoryContent(
            sessions = sessions,
            filtered = filtered,
            query = query,
            onQueryChange = { query = it },
            onNewChat = onNewChat,
            onSelectSession = onSelectSession,
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight),
        )
    }
}

/** 弹窗的纯内容（不含 sheet 容器与存储读取），便于复用与 @Preview。 */
@Composable
private fun ChatHistoryContent(
    sessions: List<ChatSession>,
    filtered: List<ChatSession>,
    query: String,
    onQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (ChatSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 列表消费不掉的滚动 / fling 全部在此吃掉，不再上抛给 ModalBottomSheet，
    // 避免内容不足一屏时手势在列表与弹窗之间来回争夺而剧烈抖动。
    val keepScrollInList = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset = available

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity = available
        }
    }
    Column(
        modifier = modifier
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
        SearchField(query = query, onQueryChange = onQueryChange)

        Spacer(Modifier.height(20.dp))

        Text("Recent", fontSize = 14.sp, color = SubColor)
        Spacer(Modifier.height(4.dp))

        if (filtered.isEmpty()) {
            Text(
                if (sessions.isEmpty()) "No chat history yet" else "No matching chats",
                fontSize = 14.sp,
                color = SubColor,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            // 用 LazyColumn 作为唯一滚动容器：与 ModalBottomSheet 的嵌套滚动正确协作，
            // fling 到边界时不会与弹窗拖拽来回争夺手势（避免剧烈抖动）。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(keepScrollInList),
            ) {
                items(filtered) { session ->
                    ChatRow(title = session.title, onClick = { onSelectSession(session) })
                }
            }
        }
    }
}

// ── Preview（预览内容层；ModalBottomSheet 为窗口层，静态预览不渲染） ──

private fun previewSessions() = listOf(
    ChatSession("1", "Trip planning for Tokyo", 0L, emptyList()),
    ChatSession("2", "Summarize meeting notes", 0L, emptyList()),
    ChatSession("3", "Brainstorm app names", 0L, emptyList()),
)

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, heightDp = 560, name = "ChatHistory · With Sessions")
@Composable
private fun ChatHistoryContentPreview() {
    val sessions = previewSessions()
    AppTheme {
        ChatHistoryContent(
            sessions = sessions,
            filtered = sessions,
            query = "",
            onQueryChange = {},
            onNewChat = {},
            onSelectSession = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, heightDp = 400, name = "ChatHistory · Empty History")
@Composable
private fun ChatHistoryContentEmptyPreview() {
    AppTheme {
        ChatHistoryContent(
            sessions = emptyList(),
            filtered = emptyList(),
            query = "",
            onQueryChange = {},
            onNewChat = {},
            onSelectSession = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF7, heightDp = 400, name = "ChatHistory · No Search Results")
@Composable
private fun ChatHistoryContentNoMatchPreview() {
    AppTheme {
        ChatHistoryContent(
            sessions = previewSessions(),
            filtered = emptyList(),
            query = "xyz",
            onQueryChange = {},
            onNewChat = {},
            onSelectSession = {},
        )
    }
}
