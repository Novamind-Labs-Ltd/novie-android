package com.novamind.app.common.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.novamind.app.common.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * 把整本 PDF 一直打开，按需逐页渲染（不一次性渲染全部页）。
 *
 * - [PdfRenderer] 非线程安全且同一时刻只能 open 一页，故所有渲染用 [mutex] 串行化，并跑在 IO 线程。
 * - 调用方负责在离开界面时 [close]，释放渲染器与文件描述符。
 * - 配合 [PdfPageCache] 做 LRU 缓存 + 回收，内存峰值与页数解耦（≈ 缓存容量 × 单页大小）。
 */
class PdfRenderSession private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val targetWidth: Int,
) : Closeable {

    val pageCount: Int = renderer.pageCount

    /** 首页宽高比 (width / height)，供调用方按比例确定显示高度。竖版 A4 约 0.707。 */
    val firstPageAspect: Float = run {
        val page = renderer.openPage(0)
        try {
            if (page.height > 0) page.width.toFloat() / page.height else 0.707f
        } finally {
            page.close()
        }
    }

    private val mutex = Mutex()
    @Volatile private var closed = false

    /** 渲染第 [index] 页为位图；已关闭或越界返回 null。串行执行。 */
    suspend fun renderPage(index: Int): Bitmap? = mutex.withLock {
        if (closed || index < 0 || index >= renderer.pageCount) return@withLock null
        withContext(Dispatchers.IO) {
            if (closed) return@withContext null
            val page = renderer.openPage(index)
            try {
                val scale = targetWidth.toFloat() / page.width
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        /** 打开本地 PDF 文件；失败或页数为 0 返回 null（已自动释放资源）。 */
        fun open(file: File, targetWidth: Int): PdfRenderSession? {
            if (!file.exists() || targetWidth <= 0) return null
            val pfd = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull() ?: return null
            val renderer = runCatching { PdfRenderer(pfd) }.getOrElse {
                runCatching { pfd.close() }
                return null
            }
            if (renderer.pageCount == 0) {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
                return null
            }
            return PdfRenderSession(pfd, renderer, targetWidth)
        }
    }
}

/**
 * 页面位图的 LRU 缓存：命中直接返回，未命中调用 [session] 渲染并放入缓存；
 * 超出 [capacity] 时回收最久未使用的位图（[Bitmap.recycle]），使内存峰值恒定。
 *
 * 容量需 ≥ 同时可能在屏的页数（HorizontalPager 的 current ± beyondViewportPageCount），
 * 以保证「正在显示的页」始终是最近访问的，不会被回收（避免回收正在绘制的位图）。
 */
class PdfPageCache(
    private val session: PdfRenderSession,
    private val capacity: Int = AppConfig.Pdf.PAGE_CACHE_CAPACITY,
) {
    // accessOrder = true → 访问即移到表尾，表头恒为最久未使用。
    // 仅在 synchronized(map) 内访问；渲染是 suspend，放在锁外。
    private val map = LinkedHashMap<Int, Bitmap>(capacity, 0.75f, true)

    suspend fun get(index: Int): Bitmap? {
        synchronized(map) { map[index]?.let { return it } }

        // 渲染放在锁外，避免渲染期间阻塞其他页的缓存读取
        val rendered = session.renderPage(index) ?: return null

        synchronized(map) {
            // 期间可能已有别的协程渲染并放入同一页 → 用已有的，回收本次多余位图
            map[index]?.let { existing ->
                rendered.recycle()
                return existing
            }
            map[index] = rendered
            while (map.size > capacity) {
                val it = map.entries.iterator()
                if (!it.hasNext()) break
                val eldest = it.next()
                it.remove()
                eldest.value.recycle()
            }
            return rendered
        }
    }

    /** 离开界面时调用：回收全部缓存位图。 */
    fun clear() {
        synchronized(map) {
            map.values.forEach { runCatching { it.recycle() } }
            map.clear()
        }
    }
}
