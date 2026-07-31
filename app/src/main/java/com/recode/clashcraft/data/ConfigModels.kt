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
    val uri: Uri? = null,
    val fileName: String = "未打开配置",
    val isDirty: Boolean = false,
    val isBusy: Boolean = false,
    val parseError: String? = null,
    val summary: ConfigSummary = ConfigSummary(),
    val message: String? = null,
)

data class ClashShareRequest(
    val uri: Uri,
    val fileName: String,
)
