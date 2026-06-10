package com.novamind.app.feature.create.editor

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 把用户选中的图片拷贝到应用内部存储，避免依赖外部 URI 的临时读取权限，
 * 从而保证笔记重启后图片仍可访问。
 */
object ImageStore {

    private const val DIR = "note_images"

    /** 拷贝 [uri] 指向的图片到内部存储，返回文件绝对路径；失败返回 null。 */
    fun copyToInternal(context: Context, uri: Uri): String? = try {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
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
}
