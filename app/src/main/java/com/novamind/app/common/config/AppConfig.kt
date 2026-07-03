package com.novamind.app.common.config

import androidx.compose.ui.graphics.Color
import com.novamind.app.ui.colors.Palette

/**
 * 应用级可调参数集中处。把分散在各处的“魔法数字”收拢到这里，方便后续统一调整。
 * 按领域分组：[Editor]、[Network]、[Media]、[Pdf]、[Folder]、[RecycleBin]。
 */
object AppConfig {

    /** 文件夹相关配置。 */
    object Folder {
        /** 可选文件夹颜色：每个色系取一种代表色，取自 [Palette]。 */
        val COLORS: List<Color> = listOf(
            Palette.forrest600,
            Palette.green600,
            Palette.orange600,
            Palette.red500,
            Palette.teal600,
            Palette.slate600,
            Palette.neutral700,
        )
    }

    /** 下拉刷新组件（PullToRefresh）通用配置，供所有使用下拉刷新的页面共享。 */
    object PullRefresh {
        /** 刷新指示器随机配色池：每次开始刷新时从中随机取一色，取自 [Palette]。 */
        val INDICATOR_COLORS: List<Color> = listOf(
            Palette.forrest600,
            Palette.teal600,
            Palette.orange600,
            Palette.red500,
            Palette.slate600,
            Palette.green600,
        )
    }

    /** 笔记编辑器行为。 */
    object Editor {
        /** 自动保存防抖延时（毫秒）：停顿超过此时长才落盘。 */
        const val AUTO_SAVE_DELAY_MS = 5000L

        /** 自动保存封顶间隔（毫秒）：持续编辑不停手时，最多每隔此时长强制落盘一次。 */
        const val SAVE_MAX_INTERVAL_MS = 10_000L

        /** 撤销/重做历史最大步数。 */
        const val MAX_HISTORY = 50

        /** 单条笔记最大可输入字数（标题 + 正文合计）。 */
        const val MAX_INPUT_CHARS = 50000
    }

    /** 网络超时（秒）。 */
    object Network {
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
        const val WRITE_TIMEOUT_SECONDS = 30L
    }

    /** 媒体 / 文件相关上限与参数。 */
    object Media {
        /** 文档/附件大小上限（字节）。默认 16MB。 */
        const val MAX_DOCUMENT_SIZE = 16L * 1024 * 1024

        /** 一次最多可选择的图片数量（系统多选图片选择器）。 */
        const val MAX_IMAGE_PICK = 9

        /** 单条笔记最多附件数（图片 + PDF + Markdown 合计）。 */
        const val MAX_ATTACHMENTS = 5

        /**
         * 文档选择器允许的 MIME 类型（PDF + Markdown）。
         * .md 在各文件提供方 MIME 不统一（text/markdown、text/x-markdown、甚至 text/plain），
         * 故一并放开（代价是也会显示 .txt）；OpenDocument 只能按 MIME 过滤。
         */
        val DOCUMENT_MIME_TYPES = arrayOf(
            "application/pdf",
            "text/markdown",
            "text/x-markdown",
            "text/plain",
        )

        /** 图片解码下采样的最长边上限（像素），防 OOM。 */
        const val IMAGE_MAX_DIMENSION = 2048

        /** 图片写盘压缩质量（0~100，PNG 无损时忽略）。 */
        const val IMAGE_COMPRESS_QUALITY = 100

        /** 笔记图片导入时的 JPEG 压缩质量（0~100）。 */
        const val IMAGE_JPEG_QUALITY = 85

        /** 单个录音分片大小上限（字节）。默认 5MB。 */
        const val AUDIO_SEGMENT_BYTES = 5L * 1024 * 1024

        /** 可用空间低于此值（MB）时触发录音清理。 */
        const val STORAGE_MIN_FREE_MB = 30L

        /** 录音清理回收到此可用空间（MB）即停止。 */
        const val STORAGE_TARGET_FREE_MB = 200L

        /** 录音可发送的最短时长（秒）。 */
        const val MIN_RECORD_SECONDS = 3

        /** 峰值振幅低于此值（0..32767）视为「没有声音」。 */
        const val NO_VOICE_THRESHOLD = 1800
    }

    /** AI「Polishing」选区骨架扫光条配色（ARGB，使用处用 Color(...) 包装）。 */
    object Polish {
        const val BAR_BASE = 0xFFDFDFDF
        const val BAR_HIGHLIGHT = 0xFFF0F0F0
    }

    /** 日志系统（common/log，设计见 log-system-design.md）。 */
    object Log {
        /** 单个日志分片上限（字节），超过滚动新分片。 */
        const val MAX_FILE_BYTES = 2L * 1024 * 1024

        /** 单日日志总量上限（字节），超过删最旧分片。 */
        const val MAX_DAY_BYTES = 10L * 1024 * 1024

        /** 日志文件保留天数。 */
        const val RETENTION_DAYS = 7

        /** 可用空间低于此值（MB）时停止落盘。 */
        const val STORAGE_MIN_FREE_MB = 50L

        /** 定时 flush 间隔（毫秒）；W/E 级别另有即时 flush。 */
        const val FLUSH_INTERVAL_MS = 5_000L

        /** 内存环形缓冲条数（Debug 工具箱）。 */
        const val MEMORY_BUFFER_SIZE = 500

        /** 管道队列容量，满则丢弃新日志（不阻塞调用线程）。 */
        const val CHANNEL_CAPACITY = 1024
    }

    /** 回收站。 */
    object RecycleBin {
        /** 回收站保留天数：软删后超过该天数会被「彻底删除」。 */
        const val RETENTION_DAYS = 30
    }

    /** 分享 / 协作。 */
    object Share {
        /** 单条笔记最多可分享给的邮箱数量（Manage access 上限）。 */
        const val MAX_ACCESS_EMAILS = 15
    }

    /** PDF 阅读手势缩放与渲染缓存。 */
    object Pdf {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f

        /** 渲染位图 LRU 缓存容量（页）。 */
        const val PAGE_CACHE_CAPACITY = 5
    }
}
