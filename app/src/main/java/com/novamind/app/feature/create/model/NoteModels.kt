package com.novamind.app.feature.create.model

import java.util.UUID

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#3D7A5A",
)

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val iconEmoji: String = "📁",
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val tags: List<Tag> = emptyList(),
    val folder: Folder? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
