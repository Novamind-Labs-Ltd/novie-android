package com.novamind.app.common.config

/**
 * 应用级可调参数集中处。把分散在各处的“魔法数字”收拢到这里，方便后续统一调整。
 * 按领域分组：[Editor]、[Network]、[Media]、[Pdf]。
 */
object AppConfig {

    /** 笔记编辑器行为。 */
    object Editor {
        /** 自动保存防抖延时（毫秒）。 */
        const val AUTO_SAVE_DELAY_MS = 600L

        /** 撤销/重做历史最大步数。 */
        const val MAX_HISTORY = 50
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

        /** 图片解码下采样的最长边上限（像素），防 OOM。 */
        const val IMAGE_MAX_DIMENSION = 2048

        /** 图片写盘压缩质量（0~100）。 */
        const val IMAGE_COMPRESS_QUALITY = 100

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

    /** PDF 阅读手势缩放与渲染缓存。 */
    object Pdf {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 5f
        const val DOUBLE_TAP_SCALE = 2.5f

        /** 渲染位图 LRU 缓存容量（页）。 */
        const val PAGE_CACHE_CAPACITY = 5
    }
}
