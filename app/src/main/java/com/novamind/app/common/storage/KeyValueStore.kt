package com.novamind.app.common.storage

/**
 * 键值存储抽象层。
 *
 * 目的：业务层只依赖本接口，不直接耦合具体实现（MMKV / SharedPreferences），
 * 从而支持灰度切换、回滚与单元测试（可注入内存假实现）。
 *
 * 当前实现为 [MmkvStore]。各业务存储（InstallId / ApiConfig / ProfileStore /
 * BiometricPreferences / OnboardingStore / UpdateController 等）后续逐个改为
 * 持有一个 KeyValueStore 实例，按各自的 mmapID 分文件。
 *
 * 注意：MMKV 不存 null —— putXxx 传 null 等同删除该 key（见 [MmkvStore]）。
 */
interface KeyValueStore {

    fun getString(key: String, def: String? = null): String?
    fun putString(key: String, value: String?)

    fun getBoolean(key: String, def: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, def: Int = 0): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, def: Long = 0L): Long
    fun putLong(key: String, value: Long)

    fun contains(key: String): Boolean
    fun remove(key: String)

    /** 清空本存储（仅当前 mmapID）。谨慎使用。 */
    fun clear()
}
