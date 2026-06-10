package com.novamind.app.feature.create.model

import com.novamind.app.feature.create.folder.Folder
import com.novamind.app.feature.create.tag.Tag
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val tags: List<Tag> = emptyList(),
    val folder: Folder? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
