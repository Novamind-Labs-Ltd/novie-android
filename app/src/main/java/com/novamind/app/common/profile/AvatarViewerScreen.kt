package com.novamind.app.common.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.novamind.app.R
import com.novamind.app.ui.colors.ButtonColors
import com.novamind.app.ui.colors.Palette
import com.novamind.app.ui.colors.current
import com.novamind.app.ui.theme.AppTheme
import kotlinx.coroutines.launch
import java.io.File

// 配色：引用 ui/colors 设计系统（不使用硬编码颜色）。
// 头像查看为**固定深色**沉浸页（与图片预览/裁剪页同策略），深色底及其上元素
// 取 Palette 主题无关色；品牌强调色走语义令牌。
private val Scrim = Palette.gray900
private val OnScrim = Palette.white
private val BtnBg = Palette.white
private val BtnText = Palette.gray800
private val CircleBtnBg = Palette.gray800
private val Accent: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = ButtonColors.Brand.default.current()

/**
 * 头像查看 / 编辑页：交互参照图片预览页（沉浸式深色全屏、单击切换工具栏、下拉关闭、
 * 双指缩放 + 双击放大）。底部「更换头像」进入相册选择 → 圆形裁剪 → 保存。
 *
 * 选图与裁剪流程内聚在本页：
 * 1. 点「更换头像」拉起系统相册（仅图片）。
 * 2. 选中后进入 [AvatarCropScreen] 圆形裁剪。
 * 3. 确认裁剪后通过 [onAvatarPicked] 回传落盘路径，由上层持久化；
 *    新头像经 avatarPath 形参回流，本页即时刷新。
 *
 * @param avatarPath 当前头像文件路径，null 表示尚未设置
 * @param onBack 关闭本页
 * @param onAvatarPicked 裁剪完成后的头像文件绝对路径
 */
@Composable
fun AvatarViewerScreen(
    avatarPath: String?,
    onBack: () -> Unit,
    onAvatarPicked: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()

    // 选图 → 裁剪
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) cropUri = uri }
    fun pickFromGallery() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // 缩放 / 平移（仅在有头像时启用）
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (containerSize.width * (s - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (s - 1f) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    // 单击切换沉浸（隐藏工具栏）
    var immersive by remember { mutableStateOf(false) }

    // 下拉关闭
    var dragDownY by remember { mutableFloatStateOf(0f) }
    val dismissDistance = (containerSize.height.takeIf { it > 0 } ?: 1).toFloat()
    val dismissProgress = (dragDownY / dismissDistance).coerceIn(0f, 1f)
    val bgColor by animateColorAsState(
        targetValue = Scrim,
        animationSpec = tween(220),
        label = "avatarBg",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor.copy(alpha = 1f - dismissProgress * 0.85f))
            .onSizeChanged { containerSize = it }
            .pointerInput(avatarPath) {
                // 下拉关闭：未放大时生效
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (scale <= 1f) {
                            dragDownY = (dragDownY + dragAmount).coerceAtLeast(0f)
                            if (dragDownY > 0f) change.consume()
                        }
                    },
                    onDragEnd = {
                        if (dragDownY > dismissDistance * 0.18f) {
                            scope.launch {
                                animate(dragDownY, dismissDistance, animationSpec = tween(200)) { v, _ -> dragDownY = v }
                                onBack()
                            }
                        } else {
                            scope.launch {
                                animate(dragDownY, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragDownY = v }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { animate(dragDownY, 0f, animationSpec = tween(150)) { v, _ -> dragDownY = v } }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarPath != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragDownY
                        val s = 1f - dismissProgress * 0.4f
                        scaleX = s
                        scaleY = s
                    }
                    .pointerInput(avatarPath) {
                        detectTapGestures(
                            onTap = { immersive = !immersive },
                            onDoubleTap = {
                                val target = if (scale > 1f) 1f else 2.5f
                                val startS = scale
                                val startO = offset
                                val targetO = if (target > 1f) clampOffset(startO, target) else Offset.Zero
                                scope.launch {
                                    animate(0f, 1f, animationSpec = tween(250)) { t, _ ->
                                        scale = lerp(startS, target, t)
                                        offset = Offset(
                                            lerp(startO.x, targetO.x, t),
                                            lerp(startO.y, targetO.y, t),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    .pointerInput(avatarPath) {
                        // 仅在「双指捏合」或「已放大」时消费事件做缩放/平移；
                        // 单指且未放大时不消费 → 交给外层下拉关闭手势。
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes.count { it.pressed }
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (pointers >= 2 || scale > 1f) {
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    scale = newScale
                                    offset = if (newScale > 1f) clampOffset(offset + pan, newScale) else Offset.Zero
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = "头像",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        } else {
            // 空状态：尚未设置头像
            EmptyAvatar(onClick = { pickFromGallery() })
        }

        // 顶栏：返回 | 标题（沉浸或下拉时淡出）
        AnimatedVisibility(
            visible = !immersive && dragDownY == 0f,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CircleIconButton(R.drawable.ic_arrow_back, "返回", onClick = onBack)
                    Text("头像", color = OnScrim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Box(modifier = Modifier.size(44.dp)) // 占位保持标题居中
                }

                // 底部「更换头像」
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    ChangeAvatarButton(onClick = { pickFromGallery() })
                }
            }
        }

        // 裁剪页覆盖在最上层，确认后回传路径
        cropUri?.let { uri ->
            AvatarCropScreen(
                sourceUri = uri,
                onCancel = { cropUri = null },
                onConfirm = { path ->
                    cropUri = null
                    // 复位查看态，确保新头像完整居中显示
                    scale = 1f
                    offset = Offset.Zero
                    onAvatarPicked(path)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyAvatar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape)
            .background(CircleBtnBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = OnScrim),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("👤", fontSize = 64.sp)
    }
}

@Composable
private fun ChangeAvatarButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(BtnBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("更换头像", color = BtnText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircleIconButton(iconResId: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = BtnBg, shadowElevation = 2.dp) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = contentDescription,
                tint = BtnText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF101010, showSystemUi = true)
@Composable
private fun AvatarViewerEmptyPreview() {
    AppTheme {
        AvatarViewerScreen(avatarPath = null, onBack = {}, onAvatarPicked = {})
    }
}
