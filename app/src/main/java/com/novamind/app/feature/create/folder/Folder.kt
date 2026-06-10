package com.novamind.app.feature.create.folder

import java.util.UUID

data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)

val defaultFolders = listOf(
    Folder(id = "f1", name = "Work"),
    Folder(id = "f2", name = "Personal"),
    Folder(id = "f3", name = "Projects"),
    Folder(id = "f4", name = "Archive"),
    Folder(id = "f5", name = "Ideas"),
)
