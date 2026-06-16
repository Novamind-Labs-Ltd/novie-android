package com.novamind.app.feature.asknovie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.ui.components.VoiceRecordingBar

private val Bg = Color(0xFFF1EEE6)
private val Card = Color(0xFFFFFFFF)
private val TextTitle = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Dark = Color(0xFF1A1A1A)
private val ChipText = Color(0xFF3A3A3A)

/** 预设的快捷建议（点击填入输入框）。 */
private val suggestions = listOf(
    "Help me brainstorm",
    "Who have I promised to follow up",
    "Summarize my notes",
)

/**
 * Ask Novie 聊天入口页（空状态）。匹配设计稿：
 * 顶部返回/历史/更多，中部问候，底部快捷建议 + 输入框（含 + 与麦克风）。
 *
 * @param userName 问候语显示的名字
 * @param onBack 返回上一页
 * @param onSend 发送消息（当前留空，后续可接入对话流）
 */
@Composable
fun AskNovieScreen(
    userName: String = "Jerry",
    onBack: () -> Unit = {},
    onSend: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    // 点麦克风后进入录音状态
    var isRecording by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // 录音时系统返回先退出录音
    androidx.activity.compose.BackHandler(enabled = isRecording) { isRecording = false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
    ) {
        // ── 顶部栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_arrow_back, "返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            // 历史 + 更多 合并胶囊
            Surface(color = Card, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(R.drawable.ic_history, "历史")
                    BareIconButton(R.drawable.ic_more, "更多")
                }
            }
        }

        // ── 中部问候 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Hi, $userName",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTitle,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "What’s on your mind?",
                    fontSize = 15.sp,
                    color = TextSub,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── 底部：录音条 / 快捷建议 + 输入框 ──
        if (isRecording) {
            VoiceRecordingBar(
                onCancel = { isRecording = false },
                onConfirm = { _ ->
                    // TODO: 保存录音并发送（需 MediaRecorder + RECORD_AUDIO 权限）
                    isRecording = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            // 建议 chips（横向滚动）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                suggestions.forEach { s ->
                    SuggestionChip(text = s, onClick = { input = s })
                }
            }

            Spacer(Modifier.height(12.dp))

            // 输入框
            Surface(color = Card, shape = RoundedCornerShape(28.dp), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(R.drawable.ic_add, "添加")

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (input.isEmpty()) {
                            Text("Message with Novie", color = TextSub, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(color = TextTitle, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Dark),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (input.isNotBlank()) {
                                        onSend(input.trim())
                                        input = ""
                                    }
                                },
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 麦克风（深色圆形）
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Dark)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, color = Color.White),
                                onClick = {
                                    // 点麦克风 → 收键盘并弹出录音条
                                    keyboardController?.hide()
                                    isRecording = true
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_mic),
                            contentDescription = "语音",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CircleIconButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    Surface(color = Card, shape = CircleShape, shadowElevation = 1.dp) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = desc,
                tint = TextTitle,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun BareIconButton(iconRes: Int, desc: String, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(iconRes),
            contentDescription = desc,
            tint = TextTitle,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(50), shadowElevation = 1.dp) {
        Text(
            text = text,
            color = ChipText,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun AskNovieScreenPreview() {
    AskNovieScreen()
}
