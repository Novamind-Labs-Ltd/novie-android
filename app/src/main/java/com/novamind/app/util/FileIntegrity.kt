package com.novamind.app.util

import java.io.File
import java.security.MessageDigest

/**
 * 文件完整性工具：分片用 SHA-256 校验，录音整体用「分片哈希再哈希」（hash-of-hashes）。
 * 所有方法均做流式读取，避免大文件一次性载入内存。建议在 IO 线程调用。
 */
object FileIntegrity {

    /** 计算文件的 SHA-256（小写十六进制）。 */
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    /**
     * 录音整体哈希：按顺序把各分片哈希用换行拼接后再求一次 SHA-256。
     * 同时绑定了分片内容、顺序与数量；后端用收到的分片哈希按同样规则即可重算比对。
     */
    fun recordingHash(segmentHashesInOrder: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(segmentHashesInOrder.joinToString("\n").toByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
