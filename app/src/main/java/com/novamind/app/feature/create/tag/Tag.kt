package com.novamind.app.feature.create.tag

import java.util.UUID

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#3D7A5A",
)

val defaultTags = listOf(
    Tag(id = "t1", name = "Research", colorHex = "#3D7A5A"),
    Tag(id = "t2", name = "Strategy", colorHex = "#7A6D3D"),
    Tag(id = "t3", name = "Design", colorHex = "#3D5A7A"),
    Tag(id = "t4", name = "Meeting", colorHex = "#7A3D5A"),
    Tag(id = "t5", name = "Personal", colorHex = "#5A3D7A"),
    Tag(id = "t6", name = "Product", colorHex = "#3D7A6D"),
)
