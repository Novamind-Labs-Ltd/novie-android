package com.novamind.app.feature.asknovie

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.colors.BackgroundColors
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.TextColors
import com.novamind.app.ui.colors.current

// 配色：统一引用 ui/colors 设计系统令牌，随主题深浅自动解析（不使用硬编码颜色）
private val SheetBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Page.default.current()
private val TitleColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val SubColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.tertiary.current()
private val ItemColor: Color
    @Composable @ReadOnlyComposable get() = TextColors.Primary.default.current()
private val DarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.background.current()
private val OnDarkPill: Color
    @Composable @ReadOnlyComposable get() = ButtonColors.Primary.text.current()
private val SearchBg: Color
    @Composable @ReadOnlyComposable get() = BackgroundColors.Surface.default.current()

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
    ) {
        // 固定较高的整体高度（用屏幕高度的固定比例，避免相对约束在拖动时抖动）
        val sheetHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.85f
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
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

            if (filtered.isEmpty()) {
                Text(
                    if (sessions.isEmpty()) "暂无历史会话" else "没有匹配的会话",
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
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(DarkPill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = OnDarkPill),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_chat),
            contentDescription = null,
            tint = OnDarkPill,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("New chat", color = OnDarkPill, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
