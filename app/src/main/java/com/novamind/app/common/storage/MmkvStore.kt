package com.novamind.app.common.storage

import com.tencent.mmkv.MMKV

/**
 * [KeyValueStore] 的 MMKV 实现。
 *
 * 每个实例对应一个独立的 mmapID（相当于一个 SharedPreferences 文件），
 * 按业务模块隔离，例如 `MmkvStore("api_config")`、`MmkvStore("profile")`。
 *
 * 前置条件：必须已在 Application.onCreate 中调用过 `MMKV.initialize(context)`，
 * 否则 [MMKV.mmkvWithID] 会抛异常。
 *
 * @param id     mmapID；迁移期建议沿用旧 SharedPreferences 的文件名，便于一次性导入。
 * @param cryptKey 可选 AES 加密密钥；为敏感数据（如设备/安装标识）提供加密落盘。
 */
class MmkvStore(
    id: String,
    cryptKey: String? = null,
) : KeyValueStore {

    private val kv: MMKV = MMKV.mmkvWithID(id, MMKV.SINGLE_PROCESS_MODE, cryptKey)

    override fun getString(key: String, def: String?): String? = kv.decodeString(key, def)

    /** MMKV 不存 null：传 null 视为删除该 key。 */
    override fun putString(key: String, value: String?) {
        if (value == null) kv.removeValueForKey(key) else kv.encode(key, value)
    }

    override fun getBoolean(key: String, def: Boolean): Boolean = kv.decodeBool(key, def)
    override fun putBoolean(key: String, value: Boolean) { kv.encode(key, value) }

    override fun getInt(key: String, def: Int): Int = kv.decodeInt(key, def)
    override fun putInt(key: String, value: Int) { kv.encode(key, value) }

    override fun getLong(key: String, def: Long): Long = kv.decodeLong(key, def)
    override fun putLong(key: String, value: Long) { kv.encode(key, value) }

    override fun contains(key: String): Boolean = kv.containsKey(key)
    override fun remove(key: String) { kv.removeValueForKey(key) }
    override fun clear() { kv.clearAll() }
}
