package com.novamind.app.common.config

/**
 * 应用级可调参数集中处。把分散在各处的“魔法数字”收拢到这里，方便后续统一调整。
 */
object AppConfig {

    /** 文档/附件大小上限（字节）。默认 16MB。 */
    const val MAX_DOCUMENT_SIZE: Long = 16L * 1024 * 1024
}
