package com.recode.clashcraft.data

import android.net.Uri

data class GroupInfo(
    val name: String,
    val type: String,
    val proxies: List<String>,
    val providers: List<String>,
)

data class ConfigSummary(
    val proxyCount: Int = 0,
    val groupCount: Int = 0,
    val ruleCount: Int = 0,
    val proxyProviderCount: Int = 0,
    val ruleProviderCount: Int = 0,
    val dnsEnabled: Boolean = false,
    val groups: List<GroupInfo> = emptyList(),
    val directProxyNames: Set<String> = emptySet(),
)

data class EditorState(
    val text: String = "",
    val revision: Long = 0,
    val root: Map<String, Any?> = emptyMap(),
    val uri: Uri? = null,
    val fileName: String = "未打开配置",
    val isDirty: Boolean = false,
    val isBusy: Boolean = false,
    val parseError: String? = null,
    val summary: ConfigSummary = ConfigSummary(),
    val message: String? = null,
    val pendingImports: List<ImportedProfile> = emptyList(),
)

data class ClashShareRequest(
    val uri: Uri,
    val fileName: String,
)

data class ImportedProfile(
    val fileName: String,
    val displayName: String,
    val text: String,
)

data class ConfigReadResult(
    val profiles: List<ImportedProfile>,
    val isArchive: Boolean,
)

sealed interface ConfigPathPart {
    data class Key(val value: String) : ConfigPathPart
    data class Index(val value: Int) : ConfigPathPart
}

data class ConfigPath(val parts: List<ConfigPathPart> = emptyList()) {
    fun key(value: String) = ConfigPath(parts + ConfigPathPart.Key(value))
    fun index(value: Int) = ConfigPath(parts + ConfigPathPart.Index(value))

    companion object {
        val Root = ConfigPath()
    }
}

enum class ConfigValueType {
    TEXT,
    NUMBER,
    BOOLEAN,
    MAP,
    LIST,
    NULL,
}

data class CustomRuleRequest(
    val type: String,
    val payload: String,
    val target: String,
    val noResolve: Boolean = false,
)

data class RuleAddResult(
    val index: Int,
    val rule: String,
    val existed: Boolean,
)
