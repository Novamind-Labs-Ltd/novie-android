package com.novamind.app.feature.create.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.novamind.app.common.config.AppConfig
import java.io.File
import java.util.UUID

/**
 * 把用户选中的图片 / 文件拷贝到应用内部存储，避免依赖外部 URI 的临时读取权限，
 * 从而保证笔记重启后内容仍可访问。同时提供相机拍照的目标文件与 FileProvider URI。
 */
object ImageStore {

    private const val IMAGE_DIR = "note_images"
    private const val FILE_DIR = "note_files"

    /** 拷贝 [uri] 指向的图片到内部存储，返回文件绝对路径；失败返回 null。 */
    fun copyToInternal(context: Context, uri: Uri): String? = try {
        val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val ext = context.contentResolver.getType(uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"
        val dest = File(dir, "${UUID.randomUUID()}.$ext")
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (ok) dest.absolutePath else null
    } catch (_: Exception) {
        null
    }

    /** 导入图片结果：内部存储路径 + 像素宽高（供列表用 aspectRatio 预留高度，避免布局跳动）。 */
    data class SavedImage(val path: String, val width: Int, val height: Int)

    /**
     * 导入相册图片到内部存储：解码时下采样到 [AppConfig.Media.IMAGE_MAX_DIMENSION] 以内，
     * 再以 JPEG（[AppConfig.Media.IMAGE_JPEG_QUALITY]）写盘，显著降低文件体积与后续解码开销。
     * 解码失败时回退为原样拷贝（宽高记 0）。建议在 IO 线程调用。
     */
    fun importImage(context: Context, uri: Uri): SavedImage? {
        val bitmap = decodeBitmap(context, uri)
            ?: return copyToInternal(context, uri)?.let { SavedImage(it, 0, 0) }
        return try {
            val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            val w = bitmap.width
            val h = bitmap.height
            dest.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Media.IMAGE_JPEG_QUALITY, out)
            }
            SavedImage(dest.absolutePath, w, h)
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 相机拍照后处理：把已写入 [path] 的原图就地解码下采样，再以 JPEG 覆盖写回，返回宽高。
     * 解码失败则保留原图（宽高记 0）。建议在 IO 线程调用。
     */
    fun finalizeCaptured(context: Context, path: String): SavedImage {
        val file = File(path)
        val bitmap = decodeBitmap(context, Uri.fromFile(file)) ?: return SavedImage(path, 0, 0)
        return try {
            val w = bitmap.width
            val h = bitmap.height
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, AppConfig.Media.IMAGE_JPEG_QUALITY, out)
            }
            SavedImage(path, w, h)
        } catch (_: Exception) {
            SavedImage(path, 0, 0)
        } finally {
            bitmap.recycle()
        }
    }

    /** 拷贝任意文档到内部存储，返回（绝对路径，展示文件名）；失败返回 null。 */
    fun copyFileToInternal(context: Context, uri: Uri): Pair<String, String>? = try {
        val dir = File(context.filesDir, FILE_DIR).apply { mkdirs() }
        val displayName = queryDisplayName(context, uri) ?: "Document"
        // 落盘文件名加 UUID 前缀避免重名，保留原始名（含后缀）
        val dest = File(dir, "${UUID.randomUUID()}_$displayName")
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (ok) dest.absolutePath to displayName else null
    } catch (_: Exception) {
        null
    }

    /**
     * 为相机拍照创建目标：在 note_images 下建空文件，返回（绝对路径，可写入的 content:// URI）。
     * 拍照成功后该路径即为图片文件，可直接作为 ImageBlock 的 path。
     */
    fun createCaptureTarget(context: Context): Pair<String, Uri>? = try {
        val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        dest.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        dest.absolutePath to uri
    } catch (_: Exception) {
        null
    }

    /**
     * 解码 [uri] 指向的图片为 [Bitmap]，按 EXIF 方向自动旋正，并按 [maxDimension] 下采样防止 OOM。
     * 用于头像裁剪编辑页。失败返回 null。
     */
    fun decodeBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = AppConfig.Media.IMAGE_MAX_DIMENSION,
    ): Bitmap? = try {
        // 1) 先读出宽高，计算合适的 inSampleSize（下采样）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxSide / sample > maxDimension) sample *= 2

        // 2) 实际解码：不透明照片用 RGB_565（2 字节/像素，内存减半，视觉几乎无损）
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        // 3) 读取 EXIF 方向并旋正
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        applyExifOrientation(raw, orientation)
    } catch (_: Exception) {
        null
    }

    /** 把 [bitmap] 以 PNG（保留透明通道）写入内部存储，返回绝对路径；失败返回 null。 */
    fun saveAvatarPng(context: Context, bitmap: Bitmap): String? = try {
        val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val dest = File(dir, "avatar_${UUID.randomUUID()}.png")
        dest.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, AppConfig.Media.IMAGE_COMPRESS_QUALITY, out)
        }
        dest.absolutePath
    } catch (_: Exception) {
        null
    }

    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        return try {
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            if (rotated != src) src.recycle()
            rotated
        } catch (_: Exception) {
            src
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
