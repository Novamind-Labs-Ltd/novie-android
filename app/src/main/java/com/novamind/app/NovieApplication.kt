package com.novamind.app

import android.app.Application
import com.novamind.app.common.net.ApiConfig
import com.novamind.app.common.net.CommonHeaders
import com.novamind.app.common.push.PushChannels
import com.novamind.app.data.NoteRepository
import com.novamind.app.data.RoomNoteRepository
import com.novamind.app.data.db.AppDatabase
import com.tencent.mmkv.MMKV

class NovieApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val noteRepository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }

    override fun onCreate() {
        super.onCreate()
        // 键值存储初始化：必须最先调用，KeyValueStore/MmkvStore 依赖它
        MMKV.initialize(this)
        // 载入 API 域名（环境）选择，供 NetworkModule 读取
        ApiConfig.init(this)
        // 构建并缓存 HTTPS 公用头部（平台/版本/设备标识等静态字段）
        CommonHeaders.init(this)
        // 预创建 FCM 默认通知渠道（后台推送到达时渠道须已存在）
        PushChannels.ensureDefault(this)
    }
}
