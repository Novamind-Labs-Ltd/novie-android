package com.novamind.app.feature.library.components

import com.novamind.app.feature.create.model.NoteItem
import com.novamind.app.feature.library.LibraryFolder

internal val sampleNotes = listOf(
    NoteItem("1", "Q3 KPIs", "Discussed Q3 KPIs. John to finalize the report by Thursday. Next meeting: Monday at 10 AM.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("2", "Team sync", "Team sync: Marketing launch on track. Felix leads HK event. RSVP for offsite by Friday.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("3", "Client call", "Client call notes: Requirements updated. Development starts Monday. QA testing scheduled for July.", tags = listOf("Work"), folderName = "Work", updatedAt = System.currentTimeMillis()),
    NoteItem("4", "Team retro", "Sprint retrospective notes.", tags = listOf("Personal"), folderName = "Personal", updatedAt = System.currentTimeMillis()),
)

internal val sampleFolders = listOf(
    LibraryFolder("folder-work", "Work", 8, colorHex = "#388E64"),
    LibraryFolder("folder-projects", "Projects", 5, colorHex = "#FF8C00"),
    LibraryFolder("folder-personal", "Personal", 3, colorHex = "#4A8292"),
    LibraryFolder("folder-unfiled", "Unfiled", 2),
)
