package com.novamind.app.feature.create.folder

import java.util.UUID

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)
