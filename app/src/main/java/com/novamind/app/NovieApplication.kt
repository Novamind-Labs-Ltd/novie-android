package com.novamind.app

import android.app.Application
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.RoomNoteRepository
import com.novamind.app.data.db.AppDatabase

class NovieApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val noteRepository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }
}
