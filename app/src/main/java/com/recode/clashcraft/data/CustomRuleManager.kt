package com.recode.clashcraft.data

object CustomRuleManager {
    private val noResolveTypes = setOf("GEOIP", "IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR")

    fun insert(currentRules: List<String>, request: CustomRuleRequest): Pair<List<String>, RuleAddResult> {
        val type = request.type.trim().uppercase()
        val payload = request.payload.trim()
        val target = request.target.trim()
        require(type.isNotEmpty()) { "请选择规则类型" }
        require(type == "MATCH" || payload.isNotEmpty()) { "匹配内容不能为空" }
        require(target.isNotEmpty()) { "请选择目标策略组" }
        val rule = buildString {
            append(type)
            if (type != "MATCH") append(',').append(payload)
            append(',').append(target)
            if (request.noResolve && type in noResolveTypes) append(",no-resolve")
        }
        val ruleKey = identity(rule)
        val existingIndex = currentRules.indexOfFirst { identity(it) == ruleKey }
        if (existingIndex >= 0) {
            return currentRules to RuleAddResult(existingIndex, currentRules[existingIndex], existed = true)
        }
        val insertAt = currentRules.indexOfFirst { it.substringBefore(',').trim().equals("MATCH", true) }
            .let { if (it < 0) currentRules.size else it }
        val updated = currentRules.toMutableList().apply { add(insertAt, rule) }
        return updated to RuleAddResult(insertAt, rule, existed = false)
    }

    private fun identity(rule: String): String {
        val parts = rule.split(',').map(String::trim)
        val type = parts.firstOrNull()?.uppercase().orEmpty()
        return if (type == "MATCH") "MATCH" else "$type,${parts.getOrNull(1).orEmpty().lowercase()}"
    }
}
