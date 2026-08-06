package com.recode.clashcraft.data

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object MihomoYaml {
    fun parse(text: String): LinkedHashMap<String, Any?> {
        require(text.isNotBlank()) { "配置内容为空" }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 500
            codePointLimit = 64 * 1024 * 1024
            nestingDepthLimit = 200
        }
        val loaded = Yaml(SafeConstructor(options)).load<Any?>(text)
            ?: error("配置内容为空")
        require(loaded is Map<*, *>) { "顶层 YAML 必须是键值对象" }

        val result = linkedMapOf<String, Any?>()
        loaded.forEach { (key, value) ->
            require(key is String) { "顶层配置键必须是文本" }
            result[key] = mutableCopy(value)
        }
        return result
    }

    fun dump(root: Map<String, Any?>): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0
            width = 140
            lineBreak = DumperOptions.LineBreak.UNIX
        }
        return Yaml(options).dump(root).trimEnd() + "\n"
    }

    fun validate(text: String): Result<ConfigSummary> = runCatching {
        summarize(parse(text))
    }

    fun summarize(root: Map<String, Any?>): ConfigSummary {
        val proxies = root["proxies"].asMapList()
        val groups = root["proxy-groups"].asMapList().mapNotNull { group ->
            val name = group["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GroupInfo(
                name = name,
                type = group["type"]?.toString().orEmpty(),
                proxies = group["proxies"].asStringList(),
                providers = group["use"].asStringList(),
            )
        }
        val dnsEnabled = (root["dns"] as? Map<*, *>)?.get("enable") as? Boolean ?: false
        return ConfigSummary(
            proxyCount = proxies.size,
            groupCount = groups.size,
            ruleCount = (root["rules"] as? List<*>)?.size ?: 0,
            proxyProviderCount = (root["proxy-providers"] as? Map<*, *>)?.size ?: 0,
            ruleProviderCount = (root["rule-providers"] as? Map<*, *>)?.size ?: 0,
            dnsEnabled = dnsEnabled,
            groups = groups,
            directProxyNames = proxies.mapNotNullTo(linkedSetOf()) { it["name"]?.toString() },
        )
    }

    /** 深拷贝配置树，返回可变结构，供需要就地修改的流程（如 RuleWizard）复用，避免重复 dump/parse。 */
    @Suppress("UNCHECKED_CAST")
    internal fun copy(root: Map<String, Any?>): LinkedHashMap<String, Any?> =
        mutableCopy(root) as LinkedHashMap<String, Any?>

    private fun mutableCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().apply {
            value.forEach { (key, child) -> put(key.toString(), mutableCopy(child)) }
        }
        is List<*> -> value.mapTo(mutableListOf<Any?>()) { mutableCopy(it) }
        else -> value
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMapList(): List<MutableMap<String, Any?>> =
    (this as? List<*>)?.mapNotNull { it as? MutableMap<String, Any?> } ?: emptyList()

internal fun Any?.asStringList(): List<String> =
    (this as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
