package com.novamind.app.debug.imageupload

/**
 * 图片上传测试页 UI 状态（不可变，单一数据源）。
 *
 * 选中的图片本身（Uri / 预览图）由 Compose 侧临时持有，不放进可观察状态；
 * 这里仅保留请求配置与服务端返回结果。
 */
data class ImageUploadUiState(
    val url: String = DEFAULT_URL,        // 可配置的上传地址
    val fieldName: String = DEFAULT_FIELD, // multipart 表单字段名
    val pickedName: String = "",          // 已选图片文件名（展示用）
    val pickedSize: Long = 0L,            // 已选图片字节数（展示用）
    val isUploading: Boolean = false,
    val responseCode: Int = 0,            // 最近一次响应状态码
    val responsePreview: String = "",     // 响应体预览（前 800 字）
    val uploadedUrl: String = "",         // 从响应中解析出的图片链接
    val errorMessage: String? = null,
    val hasUploaded: Boolean = false,     // 是否已发起过至少一次上传
) {
    val hasImage: Boolean get() = pickedName.isNotEmpty()
    val hasUploadedUrl: Boolean get() = uploadedUrl.isNotEmpty()

    companion object {
        const val DEFAULT_URL = "http://121.41.207.114:8080/api/upload"
        const val DEFAULT_FIELD = "file"
    }
}

/** UI → ViewModel 事件。 */
sealed interface ImageUploadEvent {
    data class UpdateUrl(val value: String) : ImageUploadEvent
    data class UpdateFieldName(val value: String) : ImageUploadEvent

    /** 用户选好图片，携带读取到的元信息与字节内容。 */
    data class ImagePicked(
        val name: String,
        val size: Long,
        val mime: String,
        val bytes: ByteArray,
    ) : ImageUploadEvent {
        // bytes 为数组，需自定义 equals/hashCode 以满足 data class 语义。
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImagePicked) return false
            return name == other.name && size == other.size &&
                mime == other.mime && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + size.hashCode()
            result = 31 * result + mime.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data object Upload : ImageUploadEvent
    data object ClearImage : ImageUploadEvent
}
