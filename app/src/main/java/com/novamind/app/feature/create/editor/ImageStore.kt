package com.novamind.app.feature.create.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
