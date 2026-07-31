package com.recode.clashcraft.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.recode.clashcraft.data.ConfigPath
import com.recode.clashcraft.data.ConfigPathPart
import com.recode.clashcraft.data.ConfigValueType

data class ConfigEditorActions(
    val setValue: (ConfigPath, Any?) -> Unit,
    val remove: (ConfigPath) -> Unit,
    val renameKey: (ConfigPath, String, String) -> Unit,
    val addMapEntry: (ConfigPath, String, ConfigValueType) -> Unit,
    val addListItem: (ConfigPath, ConfigValueType) -> Unit,
    val moveListItem: (ConfigPath, Int, Int) -> Unit,
)

private data class ConfigSection(val title: String, val keys: List<String>)
private data class ListPosition(val parent: ConfigPath, val index: Int, val size: Int)

@Composable
fun ConfigGuiEditor(
    root: Map<String, Any?>,
    actions: ConfigEditorActions,
    modifier: Modifier = Modifier,
) {
    var showAddRoot by rememberSaveable { mutableStateOf(false) }
    val sections = remember(root.keys) { buildSections(root.keys) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("GUI 配置编辑器", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "字段按类型显示为开关、选择器、数字、文本、对象或列表。大型列表默认折叠并分批显示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(sections, key = { it.title }) { section ->
            SectionCard(section, root, actions)
        }
        item {
            OutlinedButton(
                onClick = { showAddRoot = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("添加顶层配置项") }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddRoot) {
        AddValueDialog(
            requireKey = true,
            onDismiss = { showAddRoot = false },
            onConfirm = { key, type ->
                actions.addMapEntry(ConfigPath.Root, key, type)
                showAddRoot = false
            },
        )
    }
}

@Composable
private fun SectionCard(
    section: ConfigSection,
    root: Map<String, Any?>,
    actions: ConfigEditorActions,
) {
    var expanded by rememberSaveable(section.title) {
        mutableStateOf(section.title == "基础设置")
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${section.keys.size} 个配置项", style = MaterialTheme.typography.bodySmall)
                }
                Text(if (expanded) "收起" else "展开", color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    section.keys.forEachIndexed { index, key ->
                        ConfigNodeEditor(
                            label = fieldTitle(key),
                            rawKey = key,
                            value = root[key],
                            path = ConfigPath.Root.key(key),
                            parentMapPath = ConfigPath.Root,
                            listPosition = null,
                            depth = 0,
                            actions = actions,
                        )
                        if (index != section.keys.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigNodeEditor(
    label: String,
    rawKey: String?,
    value: Any?,
    path: ConfigPath,
    parentMapPath: ConfigPath?,
    listPosition: ListPosition?,
    depth: Int,
    actions: ConfigEditorActions,
) {
    val expandable = value is Map<*, *> || value is List<*>
    var expanded by rememberSaveable(path.stableId()) { mutableStateOf(false) }
    var showRename by rememberSaveable(path.stableId()) { mutableStateOf(false) }
    var showAdd by rememberSaveable(path.stableId()) { mutableStateOf(false) }
    var visibleCount by rememberSaveable(path.stableId()) { mutableStateOf(30) }
    val indent = (depth.coerceAtMost(4) * 8).dp

    Column(Modifier.fillMaxWidth().padding(start = indent, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).then(
                    if (expandable) Modifier.clickable { expanded = !expanded } else Modifier,
                ),
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                if (rawKey != null && fieldTitle(rawKey) != rawKey) {
                    Text(rawKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expandable) {
                    val count = if (value is Map<*, *>) value.size else (value as List<*>).size
                    Text(
                        "${valueTypeLabel(value)} · $count 项 · ${if (expanded) "点击收起" else "点击展开"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            listPosition?.let { position ->
                TextButton(
                    onClick = { actions.moveListItem(position.parent, position.index, -1) },
                    enabled = position.index > 0,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("↑") }
                TextButton(
                    onClick = { actions.moveListItem(position.parent, position.index, 1) },
                    enabled = position.index < position.size - 1,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) { Text("↓") }
            }
            ValueTypeMenu(value) { type -> actions.setValue(path, defaultValue(type)) }
            if (rawKey != null && parentMapPath != null) {
                TextButton(onClick = { showRename = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("改名")
                }
            }
            TextButton(onClick = { actions.remove(path) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }

        when (value) {
            is Boolean -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (value) "已开启" else "已关闭", modifier = Modifier.weight(1f))
                Switch(checked = value, onCheckedChange = { actions.setValue(path, it) })
            }
            is Number -> NumberField(value) { actions.setValue(path, it) }
            is String -> StringField(path, value) { actions.setValue(path, it) }
            null -> Text("当前值为空（null）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Map<*, *> -> if (expanded) {
                val entries = value.entries.toList()
                entries.take(visibleCount).forEachIndexed { index, entry ->
                    val key = entry.key.toString()
                    ConfigNodeEditor(
                        label = fieldTitle(key),
                        rawKey = key,
                        value = entry.value,
                        path = path.key(key),
                        parentMapPath = path,
                        listPosition = null,
                        depth = depth + 1,
                        actions = actions,
                    )
                    if (index < minOf(entries.size, visibleCount) - 1) HorizontalDivider()
                }
                PagingAndAddButtons(
                    remaining = entries.size - visibleCount,
                    addLabel = "添加字段",
                    onMore = { visibleCount += 50 },
                    onAdd = { showAdd = true },
                )
            }
            is List<*> -> if (expanded) {
                value.take(visibleCount).forEachIndexed { index, child ->
                    ConfigNodeEditor(
                        label = "第 ${index + 1} 项",
                        rawKey = null,
                        value = child,
                        path = path.index(index),
                        parentMapPath = null,
                        listPosition = ListPosition(path, index, value.size),
                        depth = depth + 1,
                        actions = actions,
                    )
                    if (index < minOf(value.size, visibleCount) - 1) HorizontalDivider()
                }
                PagingAndAddButtons(
                    remaining = value.size - visibleCount,
                    addLabel = "添加列表项",
                    onMore = { visibleCount += 50 },
                    onAdd = { showAdd = true },
                )
            }
            else -> StringField(path, value.toString()) { actions.setValue(path, it) }
        }
    }

    if (showRename && rawKey != null && parentMapPath != null) {
        RenameKeyDialog(
            oldKey = rawKey,
            onDismiss = { showRename = false },
            onConfirm = { newKey ->
                actions.renameKey(parentMapPath, rawKey, newKey)
                showRename = false
            },
        )
    }
    if (showAdd && (value is Map<*, *> || value is List<*>)) {
        AddValueDialog(
            requireKey = value is Map<*, *>,
            onDismiss = { showAdd = false },
            onConfirm = { key, type ->
                if (value is Map<*, *>) actions.addMapEntry(path, key, type)
                else actions.addListItem(path, type)
                showAdd = false
            },
        )
    }
}

@Composable
private fun PagingAndAddButtons(
    remaining: Int,
    addLabel: String,
    onMore: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (remaining > 0) {
            OutlinedButton(onClick = onMore, modifier = Modifier.weight(1f)) {
                Text("再显示 ${minOf(50, remaining)} 项")
            }
        }
        Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text(addLabel) }
    }
}

@Composable
private fun StringField(path: ConfigPath, value: String, onValueChange: (String) -> Unit) {
    val options = knownOptions(path)
    if (options != null) {
        var open by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value.ifBlank { "请选择" }, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onValueChange(option); open = false },
                    )
                }
            }
        }
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("值") },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            minLines = if ('\n' in value) 3 else 1,
            singleLine = '\n' !in value && value.length < 100,
        )
    }
}

@Composable
private fun NumberField(value: Number, onValueChange: (Number) -> Unit) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            parseNumber(next, value)?.let(onValueChange)
        },
        label = { Text("数值") },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun ValueTypeMenu(value: Any?, onChange: (ConfigValueType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text(valueTypeLabel(value))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ConfigValueType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label()) },
                    onClick = { onChange(type); open = false },
                )
            }
        }
    }
}

@Composable
private fun AddValueDialog(
    requireKey: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, ConfigValueType) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ConfigValueType.TEXT) }
    var typeMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (requireKey) "添加配置字段" else "添加列表项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (requireKey) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("字段名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("类型：${type.label()}", modifier = Modifier.weight(1f))
                        Text("▾")
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ConfigValueType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label()) },
                                onClick = { type = option; typeMenu = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key, type) }, enabled = !requireKey || key.isNotBlank()) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RenameKeyDialog(oldKey: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var key by remember(oldKey) { mutableStateOf(oldKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改字段名") },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("字段名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(key) }, enabled = key.isNotBlank()) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun buildSections(keys: Set<String>): List<ConfigSection> {
    val groups = listOf(
        "基础设置" to setOf(
            "port", "socks-port", "redir-port", "tproxy-port", "mixed-port", "allow-lan", "bind-address",
            "mode", "log-level", "ipv6", "unified-delay", "tcp-concurrent", "find-process-mode",
            "external-controller", "external-controller-tls", "secret", "external-ui", "external-ui-url",
            "authentication", "skip-auth-prefixes", "lan-allowed-ips", "lan-disallowed-ips",
        ),
        "DNS" to setOf("dns", "hosts", "host"),
        "TUN 与流量嗅探" to setOf("tun", "sniffer", "listeners", "ntp"),
        "代理节点" to setOf("proxies"),
        "代理组" to setOf("proxy-groups"),
        "代理提供者" to setOf("proxy-providers"),
        "路由规则" to setOf("rules", "sub-rules"),
        "规则提供者" to setOf("rule-providers"),
        "配置与实验功能" to setOf("profile", "geodata-mode", "geodata-loader", "geo-auto-update", "geo-update-interval", "geox-url", "routing-mark", "experimental"),
    )
    val remaining = keys.toMutableSet()
    val result = groups.mapNotNull { (title, known) ->
        val matches = keys.filter { it in known }
        remaining.removeAll(matches.toSet())
        matches.takeIf(List<String>::isNotEmpty)?.let { ConfigSection(title, it) }
    }.toMutableList()
    if (remaining.isNotEmpty()) result += ConfigSection("其他配置", keys.filter { it in remaining })
    return result
}

private fun fieldTitle(key: String): String = FIELD_TITLES[key] ?: key

private val FIELD_TITLES = mapOf(
    "port" to "HTTP 代理端口",
    "socks-port" to "SOCKS5 代理端口",
    "mixed-port" to "混合代理端口",
    "redir-port" to "透明代理端口",
    "tproxy-port" to "TProxy 端口",
    "allow-lan" to "允许局域网连接",
    "bind-address" to "监听地址",
    "mode" to "运行模式",
    "log-level" to "日志级别",
    "ipv6" to "IPv6",
    "find-process-mode" to "进程识别模式",
    "external-controller" to "外部控制地址",
    "secret" to "控制器密钥",
    "dns" to "DNS 设置",
    "tun" to "TUN 设置",
    "sniffer" to "流量嗅探",
    "proxies" to "代理节点",
    "proxy-groups" to "代理组",
    "proxy-providers" to "代理提供者",
    "rules" to "路由规则",
    "rule-providers" to "规则提供者",
    "name" to "名称",
    "type" to "类型",
    "server" to "服务器",
    "interval" to "检查间隔",
    "tolerance" to "延迟容差",
    "url" to "测试或订阅地址",
    "enable" to "启用",
)

private fun knownOptions(path: ConfigPath): List<String>? {
    val key = (path.parts.lastOrNull() as? ConfigPathPart.Key)?.value ?: return null
    val parentKeys = path.parts.dropLast(1).mapNotNull { (it as? ConfigPathPart.Key)?.value }
    return when (key) {
        "mode" -> listOf("rule", "global", "direct")
        "log-level" -> listOf("silent", "error", "warning", "info", "debug")
        "find-process-mode" -> listOf("always", "strict", "off")
        "type" -> when {
            "proxy-groups" in parentKeys -> listOf("select", "url-test", "fallback", "load-balance", "relay")
            "proxy-providers" in parentKeys || "rule-providers" in parentKeys -> listOf("http", "file", "inline")
            else -> null
        }
        "strategy" -> if ("proxy-groups" in parentKeys) {
            listOf("consistent-hashing", "round-robin", "sticky-sessions")
        } else null
        else -> null
    }
}

private fun ConfigPath.stableId(): String = parts.joinToString("/") {
    when (it) {
        is ConfigPathPart.Key -> "k:${it.value}"
        is ConfigPathPart.Index -> "i:${it.value}"
    }
}

private fun valueTypeLabel(value: Any?): String = when (value) {
    is Map<*, *> -> "对象"
    is List<*> -> "列表"
    is Boolean -> "开关"
    is Number -> "数字"
    is String -> "文本"
    null -> "空值"
    else -> "文本"
}

private fun ConfigValueType.label(): String = when (this) {
    ConfigValueType.TEXT -> "文本"
    ConfigValueType.NUMBER -> "数字"
    ConfigValueType.BOOLEAN -> "开关"
    ConfigValueType.MAP -> "对象"
    ConfigValueType.LIST -> "列表"
    ConfigValueType.NULL -> "空值"
}

private fun defaultValue(type: ConfigValueType): Any? = when (type) {
    ConfigValueType.TEXT -> ""
    ConfigValueType.NUMBER -> 0
    ConfigValueType.BOOLEAN -> true
    ConfigValueType.MAP -> linkedMapOf<String, Any?>()
    ConfigValueType.LIST -> mutableListOf<Any?>()
    ConfigValueType.NULL -> null
}

private fun parseNumber(text: String, original: Number): Number? = when (original) {
    is Int -> text.toIntOrNull()
    is Long -> text.toLongOrNull()
    is Float -> text.toFloatOrNull()
    is Double -> text.toDoubleOrNull()
    is Short -> text.toShortOrNull()
    is Byte -> text.toByteOrNull()
    else -> text.toDoubleOrNull()
}
