package com.recode.clashcraft.data

/**
 * 规则集合的纯函数过滤工具。独立成对象便于在 JVM 单测中验证，
 * 避免把过滤逻辑与依赖 [android.content.Context] 的持久化层耦合。
 */
object RuleFilter {
    /** 只保留文本出现在 [locked] 集合中的规则，保持原始顺序。 */
    fun keepLocked(rules: List<Any?>, locked: Set<String>): List<Any?> =
        rules.filter { it.toString() in locked }
}
