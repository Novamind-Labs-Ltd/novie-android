package com.novamind.app.feature.create.folder

/**
 * 云端文件夹领域模型（对应后端 FolderView，纯 Kotlin，无框架/序列化类型）。
 * 与本地 [com.novamind.app.data.StoredFolder] 区分：这是服务端文件夹的领域投影，无颜色字段
 * （颜色目前是 App 本地概念）。
 */
data class RemoteFolder(
    val id: String,
    val name: String,
    val sortOrder: Int,
    /** 服务端统计的活跃笔记数，不包含回收站笔记。 */
    val noteCount: Long,
)

/**
 * 文件夹列表分页结果（对应后端 FolderPageView）：条目 + 下一页游标。
 * [nextCursor] 为 null 表示已到底；否则作为下一页 cursor 传回。
 */
data class RemoteFolderPage(
    val items: List<RemoteFolder>,
    val nextCursor: String?,
)
