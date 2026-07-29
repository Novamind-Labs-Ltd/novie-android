package com.novamind.app.feature.asknovie.components

import android.content.Intent
import android.util.LruCache
import com.novamind.app.util.ToastUtils
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdownFlow
import com.novamind.app.R
import com.novamind.app.feature.asknovie.AttachType
import com.novamind.app.feature.asknovie.Attachment
import com.novamind.app.feature.asknovie.ChatMessage
import com.novamind.app.feature.asknovie.Role
import com.novamind.app.ui.theme.AppTheme
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/** 语音气泡：播放/暂停 + 名称（含时长）。点击播放录音文件；预览态不创建 MediaPlayer。 */
@Composable
internal fun AudioBubble(att: Attachment) {
    val inPreview = LocalInspectionMode.current
    val player = if (inPreview) null else remember { android.media.MediaPlayer() }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() } }
    }
    player?.setOnCompletionListener { playing = false }

    Surface(
        color = Card,
        shape = RoundedCornerShape(50),
        shadowElevation = 1.dp,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = {
                        val p = player ?: return@clickable
                        runCatching {
                            if (playing) {
                                p.pause(); playing = false
                            } else {
                                if (!prepared) {
                                    p.setDataSource(att.path); p.prepare(); prepared = true
                                }
                                p.start(); playing = true
                            }
                        }
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(SendGreen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = OnSendGreen,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(att.name, color = TextTitle, fontSize = 14.sp)
        }
    }
}

/** 用户消息：图片、文件、语音和文本均靠右展示。 */
@Composable
internal fun UserBubble(msg: ChatMessage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val imageAttachments = msg.attachments.filter { it.type == AttachType.Image }
        if (imageAttachments.isNotEmpty()) {
            // Figma 1459:53678：80dp 方形缩略图、6dp 间距、整体靠右，超宽后横向滚动。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = if (msg.text.isNotEmpty()) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                imageAttachments.forEach { attachment ->
                    AttachmentChip(
                        att = attachment,
                        onRemove = {},
                        showRemove = false,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                modifier = Modifier.padding(start = 48.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // 文件附件：静态 chip（置于最前）
                msg.attachments.filter { it.type == AttachType.File }.forEach { att ->
                Surface(
                    color = AttachChipBg,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_document),
                            contentDescription = null,
                            tint = TextSub,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        // 文件名完整显示（过长则换行，不省略）
                        Text(
                            att.name,
                            color = TextTitle,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
                // 语音附件：可播放气泡
                msg.attachments.filter { it.type == AttachType.Audio }.forEach { att ->
                    AudioBubble(att)
                }
                // 文本气泡（有文字才显示）
                if (msg.text.isNotEmpty()) {
                    Surface(
                        color = UserMessageBg,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = msg.text,
                                color = TextTitle,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 助手消息：Markdown 内容（可选中复制）+ 操作行（复制/分享/朗读）。
 * [showAvatar] 为 true 时（收尾/追问语气）在左侧显示花标、且不带操作行。
 */
@Composable
internal fun AssistantText(
    text: String,
    showAvatar: Boolean = false,
    isTyping: Boolean = false,
    deferMarkdown: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    var lastHapticAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(text, isTyping) {
        if (isTyping && text.isNotEmpty()) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastHapticAt >= TYPEWRITER_HAPTIC_INTERVAL_MS) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastHapticAt = now
            }
        }
    }

    if (showAvatar) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_novie_flower),
                contentDescription = null,
                tint = TextTitle,
                modifier = Modifier.size(22.dp),
            )
            SelectionContainer {
                AssistantMessageContent(
                    text = text,
                    isStreaming = isTyping,
                    deferMarkdown = deferMarkdown,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                AssistantMessageContent(
                    text = text,
                    isStreaming = isTyping,
                    deferMarkdown = deferMarkdown,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!isTyping) {
                Spacer(Modifier.height(10.dp))
                AssistantActions(text = text)
            }
        }
    }
}

private const val TYPEWRITER_HAPTIC_INTERVAL_MS = 100L

private object MarkdownRenderCache {
    private val values = object : LruCache<String, State.Success>(500_000) {
        override fun sizeOf(key: String, value: State.Success): Int = key.length
    }

    @Synchronized fun get(content: String): State.Success? = values.get(content)

    @Synchronized fun put(content: String, state: State.Success) = values.put(content, state)
}

/**
 * SSE 流式期间只把已结束的段落交给 Markdown，正在增长的尾段使用稳定 Text。
 * 避免未闭合 Markdown 每批字符都替换整棵渲染树；流结束后再渲染完整 Markdown。
 */
@Composable
private fun AssistantMessageContent(
    text: String,
    isStreaming: Boolean,
    deferMarkdown: Boolean,
    modifier: Modifier = Modifier,
) {
    val paragraphBoundary = if (isStreaming) text.lastIndexOf("\n\n") else -1
    val completedMarkdown = if (!isStreaming) {
        text
    } else if (paragraphBoundary >= 0) {
        text.substring(0, paragraphBoundary).trimEnd()
    } else {
        ""
    }
    val activeTail = if (!isStreaming) {
        ""
    } else if (paragraphBoundary >= 0) {
        text.substring(paragraphBoundary + 2)
    } else {
        text
    }

    Column(modifier = modifier) {
        if (completedMarkdown.isNotEmpty()) {
            StableMarkdown(content = completedMarkdown, deferParsing = deferMarkdown)
        }
        if (activeTail.isNotEmpty()) {
            Text(
                text = activeTail,
                color = TextTitle,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

/**
 * Markdown 内容变化时继续展示上一次解析成功的树，直到新树准备完成。
 * 规避 renderer 默认的 Success → Loading(空 Box) → Success 闪白过程。
 */
@Composable
private fun StableMarkdown(
    content: String,
    deferParsing: Boolean,
    modifier: Modifier = Modifier,
) {
    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour) }
    val referenceLinkHandler = remember { ReferenceLinkHandlerImpl() }
    var renderedState by remember { mutableStateOf(MarkdownRenderCache.get(content)) }

    LaunchedEffect(content, deferParsing, flavour, parser, referenceLinkHandler) {
        MarkdownRenderCache.get(content)?.let {
            renderedState = it
            return@LaunchedEffect
        }
        if (deferParsing) return@LaunchedEffect
        parseMarkdownFlow(
            content = content,
            flavour = flavour,
            parser = parser,
            referenceLinkHandler = referenceLinkHandler,
        ).collect { state ->
            if (state is State.Success) {
                MarkdownRenderCache.put(content, state)
                renderedState = state
            }
        }
    }

    renderedState?.let { state ->
        Markdown(
            state = state,
            modifier = modifier,
        )
    }

    // 首次解析或内容增长期间，用普通文字补齐尚未进入成功 AST 的后缀，避免尾段消失。
    val renderedContent = renderedState?.content.orEmpty()
    val pendingText = if (content.startsWith(renderedContent)) {
        content.removePrefix(renderedContent).trimStart()
    } else {
        ""
    }
    if (pendingText.isNotEmpty()) {
        Text(
            text = pendingText,
            color = TextTitle,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
    }
}

/** 助手回复下方的操作行：复制 / 分享 / 朗读。 */
@Composable
private fun AssistantActions(text: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ActionIcon(R.drawable.ic_copy, "Copy") {
            clipboard.setText(AnnotatedString(text))
            ToastUtils.short(context, "Copied")
        }
        ActionIcon(R.drawable.ic_share, "Share") {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        }
        ActionIcon(R.drawable.ic_volume, "Read aloud") {
            // TODO: 接入 TTS 朗读（如 Android TextToSpeech）
            ToastUtils.short(context, "Read aloud coming soon")
        }
    }
}

@Composable
private fun ActionIcon(iconRes: Int, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = TextSub,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 助手「正在输入」的三点动画。 */
@Composable
internal fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(TextSub.copy(alpha = alpha)),
            )
        }
    }
}

// ─── Preview ───

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · Message Bubbles")
@Composable
private fun MessageBubblesPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            UserBubble(
                ChatMessage(
                    Role.User,
                    "Help me summarize this document",
                    listOf(Attachment(AttachType.File, "/tmp/doc.pdf", "quarterly-report.pdf")),
                ),
            )
            AssistantText(text = "Sure, here are the three key points of this quarterly report: revenue grew 12% year-over-year, gross margin stabilized, and cash flow turned positive.")
            UserBubble(ChatMessage(Role.User, "", listOf(Attachment(AttachType.Audio, "/tmp/a.m4a", "Voice 0:08"))))
            TypingIndicator()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · AudioBubble")
@Composable
private fun AudioBubblePreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AudioBubble(Attachment(AttachType.Audio, "/tmp/a.m4a", "Voice 0:08"))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · UserBubble")
@Composable
private fun UserBubblePreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            UserBubble(ChatMessage(Role.User, "Help me organize this week's meeting notes into three key points", emptyList()))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · UserBubble · With Attachment")
@Composable
private fun UserBubbleWithAttachmentPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            UserBubble(
                ChatMessage(
                    Role.User,
                    "Help me summarize this document",
                    listOf(Attachment(AttachType.File, "/tmp/doc.pdf", "quarterly-report.pdf")),
                ),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · UserBubble · Images")
@Composable
private fun UserBubbleWithImagesPreview() {
    AppTheme {
        UserBubble(
            ChatMessage(
                role = Role.User,
                text = "",
                attachments = List(5) { index ->
                    Attachment(AttachType.Image, "/tmp/photo-$index.jpg", "photo-$index.jpg")
                },
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · AssistantText")
@Composable
private fun AssistantTextPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AssistantText(
                text = "## Suggested plan\n\nHere are **three practical steps**:\n\n" +
                    "1. Define the outcome\n2. Assign an owner\n3. Review it on Friday",
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF1EEE6, name = "AskNovie · TypingIndicator")
@Composable
private fun TypingIndicatorPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            TypingIndicator()
        }
    }
}
