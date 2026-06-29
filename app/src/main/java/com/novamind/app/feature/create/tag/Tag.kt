package com.novamind.app.feature.create.tag

import java.util.UUID

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#3D7A5A",
)
