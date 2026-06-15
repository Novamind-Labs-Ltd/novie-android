package com.novamind.app.common.profile

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.R
import com.novamind.app.feature.create.editor.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Scrim = Color(0xFF101010)
private val Accent = Color(0xFF3D7A5A)
private const val OUTPUT_SIZE = 512
private const val CROP_MARGIN_DP = 24f

/**
 * 头像裁剪编辑页：用户在圆形取景框内对所选图片进行双指缩放、拖动、旋转，
 * 确认后输出「圆形透明 PNG」并通过 [onConfirm] 回调返回保存后的文件绝对路径。
 *
 * 预览与最终输出共用同一套变换公式（以取景圆直径为基准），保证所见即所得。
 */
@Composable
fun AvatarCropScreen(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (croppedPath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // 异步解码源图（含 EXIF 旋正 + 下采样）
    var bitmap by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(sourceUri) { mutableStateOf(false) }
    LaunchedEffect(sourceUri) {
        val bmp = withContext(Dispatchers.IO) { ImageStore.decodeBitmap(context, sourceUri) }
        if (bmp != null) bitmap = bmp else loadFailed = true
    }

    // 用户变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }   // 角度（度）
    var offset by remember { mutableStateOf(Offset.Zero) } // 取景框（预览像素）下相对圆心的平移
    var viewportDiameterPx by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }

    fun reset() {
        scale = 1f
        rotation = 0f
        offset = Offset.Zero
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Scrim),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(R.drawable.ic_arrow_back, "取消", onClick = onCancel)
            Spacer(Modifier.weight(1f))
            Text("编辑头像", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp)) // 占位，保持标题居中
        }

        // 裁剪取景区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                bmp != null -> CropCanvas(
                    bitmap = bmp,
                    scale = scale,
                    rotation = rotation,
                    offset = offset,
                    onDiameter = { viewportDiameterPx = it },
                    onTransform = { panChange, zoomChange, rotChange ->
                        scale = (scale * zoomChange).coerceIn(1f, 6f)
                        rotation += rotChange
                        offset += panChange
                    },
                )
                loadFailed -> Text("无法加载该图片", color = Color.White, fontSize = 14.sp)
                else -> CircularProgressIndicator(color = Color.White)
            }
        }

        // 底部操作栏
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextChip(R.drawable.ic_refresh, "重置", onClick = { reset() })
                Spacer(Modifier.width(16.dp))
                TextChip(R.drawable.ic_redo, "旋转 90°", onClick = { rotation += 90f })
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton(
                    text = "取消",
                    filled = false,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                ActionButton(
                    text = if (saving) "保存中…" else "完成",
                    filled = true,
                    enabled = bitmap != null && viewportDiameterPx > 0f && !saving,
                    modifier = Modifier.weight(1f),
                    onClick = { saving = true },
                )
            }
        }
    }

    // 保存：在后台线程裁剪 + 落盘，避免阻塞 UI
    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        val bmp = bitmap
        val diameter = viewportDiameterPx
        if (bmp == null || diameter <= 0f) { saving = false; return@LaunchedEffect }
        val path = withContext(Dispatchers.Default) {
            val cropped = cropToCircle(bmp, scale, rotation, offset, diameter)
            ImageStore.saveAvatarPng(context, cropped).also { cropped.recycle() }
        }
        saving = false
        if (path != null) onConfirm(path) else loadFailed = true
    }
}

/** 圆形取景画布：预览所见即所得。取景圆直径 = min(宽, 高) - 2*边距。 */
@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    scale: Float,
    rotation: Float,
    offset: Offset,
    onDiameter: (Float) -> Unit,
    onTransform: (pan: Offset, zoom: Float, rotation: Float) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { intSize ->
                val marginPx = CROP_MARGIN_DP * density
                val d = (minOf(intSize.width, intSize.height) - 2 * marginPx).coerceAtLeast(1f)
                onDiameter(d)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rot ->
                    onTransform(pan, zoom, rot)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val marginPx = CROP_MARGIN_DP * this.density
        val diameter = (minOf(w, h) - 2 * marginPx).coerceAtLeast(1f)
        val radius = diameter / 2f
        val cx = w / 2f
        val cy = h / 2f

        // cover：让图片初始铺满取景圆
        val baseScale = maxOf(diameter / bitmap.width, diameter / bitmap.height)
        val totalScale = baseScale * scale

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // 图片本体（与输出共用变换公式）
            val save = native.save()
            native.translate(cx + offset.x, cy + offset.y)
            native.rotate(rotation)
            native.scale(totalScale, totalScale)
            native.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, null)
            native.restoreToCount(save)

            // 圆外蒙版（rect - circle，偶奇填充）
            val scrimPath = Path().apply {
                addRect(0f, 0f, w, h, Path.Direction.CW)
                addCircle(cx, cy, radius, Path.Direction.CCW)
            }
            native.drawPath(scrimPath, Paint().apply {
                color = android.graphics.Color.argb(160, 16, 16, 16)
                isAntiAlias = true
            })
            // 圆形边框
            native.drawCircle(cx, cy, radius, Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
                color = android.graphics.Color.WHITE
                isAntiAlias = true
            })
        }
    }
}

/**
 * 把源图按当前 [scale]/[rotation]/[offset] 裁剪为圆形透明 PNG（[OUTPUT_SIZE] 见方）。
 * [viewportDiameterPx] 为预览取景圆直径；令 f = OUTPUT_SIZE / 直径，
 * 则平移与缩放均按 f 映射到输出空间，保证与预览完全一致。
 */
private fun cropToCircle(
    bitmap: Bitmap,
    scale: Float,
    rotation: Float,
    offset: Offset,
    viewportDiameterPx: Float,
): Bitmap {
    val out = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)

    val f = OUTPUT_SIZE / viewportDiameterPx
    val baseScale = maxOf(viewportDiameterPx / bitmap.width, viewportDiameterPx / bitmap.height)
    val totalScale = baseScale * scale * f

    canvas.clipPath(Path().apply {
        addCircle(OUTPUT_SIZE / 2f, OUTPUT_SIZE / 2f, OUTPUT_SIZE / 2f, Path.Direction.CW)
    })

    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        isDither = true
    }

    val save = canvas.save()
    canvas.translate(OUTPUT_SIZE / 2f + offset.x * f, OUTPUT_SIZE / 2f + offset.y * f)
    canvas.rotate(rotation)
    canvas.scale(totalScale, totalScale)
    canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, paint)
    canvas.restoreToCount(save)

    return out
}

// ---- 小型 UI 组件 ----

@Composable
private fun IconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TextChip(iconRes: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun ActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val baseColor = if (filled) Accent else Color(0x22FFFFFF)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (enabled) baseColor else baseColor.copy(alpha = 0.4f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
