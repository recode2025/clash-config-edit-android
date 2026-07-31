package com.recode.clashcraft.data

data class RuleWizardRequest(
    val packageNames: List<String>,
    val domains: List<String>,
    val groupName: String,
    val parentGroupName: String,
    val selectedProxies: List<String>,
    val includeParentProviders: Boolean,
    val providerFilter: String,
    val testUrl: String,
    val groupType: String = "url-test",
    val intervalSeconds: Int = 300,
    val toleranceMs: Int = 80,
    val forceProcessLookup: Boolean = true,
)

object RuleWizard {
    private val packagePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
    private val domainPattern = Regex("^(?:\\*\\.)?[A-Za-z0-9一-龥_-]+(?:\\.[A-Za-z0-9一-龥_-]+)+$")

    fun apply(source: String, request: RuleWizardRequest): String {
        validateRequest(request)
        val root = MihomoYaml.parse(source)
        val groups = root.getOrPutMutableList("proxy-groups")
        val parent = groups.mapNotNull { it as? MutableMap<String, Any?> }
            .firstOrNull { it["name"]?.toString() == request.parentGroupName }
            ?: error("找不到上级规则组：${request.parentGroupName}")

        require(groups.none { (it as? Map<*, *>)?.get("name")?.toString() == request.groupName }) {
            "规则组“${request.groupName}”已经存在，请换一个名称"
        }

        val parentProxies = parent["proxies"].asStringList()
        val selected = request.selectedProxies.distinct()
        require(selected.isNotEmpty() || (request.includeParentProviders && parent["use"].asStringList().isNotEmpty())) {
            "请至少选择一条线路，或启用上级组的代理提供者"
        }
        require(selected.all { it in parentProxies }) { "选择的线路不属于上级规则组" }

        val autoGroup = linkedMapOf<String, Any?>(
            "name" to request.groupName,
            "type" to request.groupType,
            "url" to request.testUrl.trim(),
            "interval" to request.intervalSeconds,
            "lazy" to false,
            "timeout" to 5000,
            "expected-status" to 204,
        )
        if (request.groupType == "url-test") autoGroup["tolerance"] = request.toleranceMs
        if (selected.isNotEmpty()) autoGroup["proxies"] = selected.toMutableList()
        if (request.includeParentProviders) {
            parent["use"].asStringList().takeIf { it.isNotEmpty() }?.let {
                autoGroup["use"] = it.toMutableList()
            }
            request.providerFilter.trim().takeIf { it.isNotEmpty() }?.let {
                autoGroup["filter"] = it
            }
        }
        groups.add(autoGroup)

        if (request.forceProcessLookup) root["find-process-mode"] = "always"
        else if (root["find-process-mode"]?.toString() == "off") root["find-process-mode"] = "strict"

        val rules = root.getOrPutMutableList("rules")
        val generatedRules = buildList {
            request.packageNames.map(String::trim).filter(String::isNotEmpty).distinct().forEach {
                add("PROCESS-NAME,$it,${request.groupName}")
            }
            request.domains.map(::normalizeDomain).filter(String::isNotEmpty).distinct().forEach {
                add("DOMAIN-SUFFIX,$it,${request.groupName}")
            }
        }
        val firstCatchAll = rules.indexOfFirst {
            val value = it?.toString()?.trimStart().orEmpty()
            value == "MATCH" || value.startsWith("MATCH,") || value.startsWith("FINAL,")
        }.let { if (it < 0) rules.size else it }
        rules.addAll(firstCatchAll, generatedRules)

        return MihomoYaml.dump(root)
    }

    private fun validateRequest(request: RuleWizardRequest) {
        require(request.groupName.isNotBlank()) { "请输入新规则组名称" }
        require(',' !in request.groupName) { "规则组名称不能包含英文逗号" }
        require(request.parentGroupName.isNotBlank()) { "请选择上级规则组" }
        require(request.groupType in setOf("url-test", "fallback")) { "自动策略类型无效" }
        require(request.packageNames.any { it.isNotBlank() } || request.domains.any { it.isNotBlank() }) {
            "请至少填写一个包名或域名"
        }
        request.packageNames.map(String::trim).filter(String::isNotEmpty).forEach {
            require(packagePattern.matches(it)) { "包名格式不正确：$it" }
        }
        request.domains.map(String::trim).filter(String::isNotEmpty).forEach {
            val normalized = normalizeDomain(it)
            require(domainPattern.matches(normalized)) { "域名格式不正确：$it" }
        }
        require(request.testUrl.startsWith("https://") || request.testUrl.startsWith("http://")) {
            "测速地址必须以 http:// 或 https:// 开头"
        }
        require(request.intervalSeconds in 30..86400) { "测速间隔应在 30～86400 秒之间" }
        require(request.toleranceMs in 0..2000) { "切换容差应在 0～2000 ms 之间" }
    }

    private fun normalizeDomain(raw: String): String = raw.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
        .removePrefix("*.")
        .trim('.')

    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any?>.getOrPutMutableList(key: String): MutableList<Any?> {
        val existing = this[key]
        if (existing == null) return mutableListOf<Any?>().also { this[key] = it }
        require(existing is List<*>) { "$key 必须是列表" }
        return if (existing is MutableList<*>) existing as MutableList<Any?> else
            existing.toMutableList().also { this[key] = it }
    }
}
