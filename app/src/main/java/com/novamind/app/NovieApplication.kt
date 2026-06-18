package com.novamind.app

import android.app.Application
import com.novamind.app.common.net.CommonHeaders
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.RoomNoteRepository
import com.novamind.app.data.db.AppDatabase

class NovieApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val noteRepository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }

    override fun onCreate() {
        super.onCreate()
        // 构建并缓存 HTTPS 公用头部（平台/版本/设备标识等静态字段）
        CommonHeaders.init(this)
    }
}
