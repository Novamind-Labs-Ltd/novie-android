package com.novamind.app.common.config

/**
 * 功能开关集中处（Function Config）：控制各功能是否对用户开放。
 *
 * 与 [AppConfig]（数值参数）区分：这里只放 **开/关** 型开关，用于按版本分期放量——
 * 第一期未开放的功能设为 false，入口以「置灰不可点」等形式保留占位，后续版本改 true 即可上线。
 *
 * 命名约定：`XXX_ENABLED`，按功能域分组注释。
 */
object FunConfig {

    // ── 附件 ──

    /** 上传文档（附件弹窗的 Document 入口）。第一期不开放：按钮置灰不可点击。 */
    const val UPLOAD_DOCUMENT_ENABLED = false
}
