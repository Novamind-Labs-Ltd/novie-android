package com.novamind.app.feature.create.model

/** 标题 + 正文的不可变快照：供撤销/重做栈与「内容未变则跳过保存」的比较。 */
internal data class TextSnapshot(val title: String, val body: String)
