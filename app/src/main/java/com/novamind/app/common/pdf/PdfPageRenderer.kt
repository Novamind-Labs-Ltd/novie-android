package com.novamind.app.common.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * 用系统 [PdfRenderer] 把 PDF 文件逐页渲染为位图。
 *
 * 注意：
 * - [PdfRenderer] 需要可随机读取(seek)的文件描述符，因此入参是本地 [File]（内部存储），
 *   而非 content Uri；从 Uri 来的需先复制到本地文件。
 * - 同一时刻只能 open 一页，逐页渲染并关闭。
 * - 渲染前用白色填充，避免透明 PDF 渲染出黑底。
 * - 一次性渲染全部页（内存随页数线性增长），适合预览；超大 PDF 慎用。
 */
object PdfPageRenderer {

    /**
     * 只渲染首页作为预览，并返回总页数。用于列表/笔记内的轻量预览，避免一次性渲染全部页。
     * @return (首页位图或 null, 总页数)
     */
    fun renderPreview(file: File, targetWidth: Int): Pair<Bitmap?, Int> {
        if (!file.exists() || targetWidth <= 0) return null to 0
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val count = renderer.pageCount
            if (count == 0) return null to 0
            val page = renderer.openPage(0)
            val bmp = try {
                val scale = targetWidth.toFloat() / page.width
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
            return bmp to count
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    fun render(file: File, targetWidth: Int): List<Bitmap> {
        if (!file.exists() || targetWidth <= 0) return emptyList()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val out = ArrayList<Bitmap>()
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                try {
                    val scale = targetWidth.toFloat() / page.width
                    val w = targetWidth
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(bmp)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return out
    }
}
