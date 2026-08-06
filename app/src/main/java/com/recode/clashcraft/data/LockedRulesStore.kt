package com.recode.clashcraft.data

import android.content.Context

/**
 * 按配置文件命名空间持久化「已上锁规则」的文本集合。
 *
 * 使用 app 私有 [android.content.SharedPreferences]，无需任何 Android 存储权限——
 * 符合本应用「零联网 · 零存储权限 · 无统计 SDK」的隐私模型（此处存储权限指外部存储，
 * app 私有沙盒不计）。每个 [configKey] 对应一个配置文件的上锁集合。
 */
class LockedRulesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(configKey: String): Set<String> =
        prefs.getStringSet(configKey, emptySet()).orEmpty().toSet()

    /** 切换某条规则的上锁状态，返回更新后的完整集合。 */
    fun toggle(configKey: String, rule: String): Set<String> {
        val updated = load(configKey).toMutableSet()
        if (!updated.add(rule)) updated.remove(rule)
        write(configKey, updated)
        return updated
    }

    /** 整体覆盖某配置的上锁集合，用于清理已不在配置中的悬空项。 */
    fun replace(configKey: String, locked: Set<String>) {
        write(configKey, locked)
    }

    private fun write(configKey: String, locked: Set<String>) {
        prefs.edit().putStringSet(configKey, locked).apply()
    }

    private companion object {
        const val PREFS_NAME = "locked_rules"
    }
}
