package com.recode.clashcraft.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.recode.clashcraft.MainViewModel
import com.recode.clashcraft.data.ConfigSummary
import com.recode.clashcraft.data.CustomRuleRequest
import com.recode.clashcraft.data.EditorState
import com.recode.clashcraft.data.GroupInfo
import com.recode.clashcraft.data.RuleAddResult
import com.recode.clashcraft.data.RuleWizardRequest
import com.recode.clashcraft.ui.theme.ClashCraftTheme
import kotlinx.coroutines.launch

private enum class AppPage(val title: String, val mark: String) {
    OVERVIEW("概览", "◫"),
    CONFIG("配置", "☷"),
    WIZARD("分流规则", "⌕"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashCraftApp(
    viewModel: MainViewModel,
    onOpen: () -> Unit,
    onImportClashMi: () -> Unit,
    onSaveAs: (String) -> Unit,
    onSaveAndShare: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(AppPage.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }

    if (state.pendingImports.isNotEmpty()) {
        ImportProfileDialog(
            profiles = state.pendingImports.map { it.displayName },
            onSelect = viewModel::selectImportedProfile,
            onDismiss = viewModel::dismissImportedProfiles,
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    ClashCraftTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = {
                        Column {
                            Text("Clash 配置工坊", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = state.fileName + if (state.isDirty) "  • 未保存" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = onOpen) { Text("打开") }
                        if (state.text.isNotBlank()) {
                            TextButton(onClick = viewModel::save) { Text("保存") }
                            TextButton(onClick = { onSaveAs(state.fileName) }) { Text("另存") }
                        }
                    },
                )
            },
            bottomBar = {
                if (state.text.isNotBlank()) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        AppPage.entries.forEach { item ->
                            NavigationBarItem(
                                selected = page == item,
                                onClick = { page = item },
                                icon = { Text(item.mark, style = MaterialTheme.typography.titleMedium) },
                                label = { Text(item.title) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.isBusy -> LoadingView()
                    state.text.isBlank() -> WelcomeView(onOpen, onImportClashMi)
                    page == AppPage.OVERVIEW -> OverviewView(
                        state = state,
                        onValidate = viewModel::validate,
                        onSaveAndShare = { onSaveAndShare(state.fileName) },
                    )
                    page == AppPage.CONFIG -> ConfigGuiEditor(
                        root = state.root,
                        actions = ConfigEditorActions(
                            setValue = viewModel::setConfigValue,
                            remove = viewModel::removeConfigValue,
                            renameKey = viewModel::renameConfigKey,
                            addMapEntry = viewModel::addConfigMapEntry,
                            addListItem = viewModel::addConfigListItem,
                            moveListItem = viewModel::moveConfigListItem,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> RuleWizardView(
                        state = state,
                        onAddCustomRule = viewModel::addCustomRule,
                        onRemoveRule = viewModel::removeRule,
                        onApply = {
                            viewModel.applyWizard(it) { page = AppPage.CONFIG }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("正在处理配置…")
        }
    }
}

@Composable
private fun WelcomeView(onOpen: () -> Unit, onImportClashMi: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("用 GUI 修改完整配置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "无需编辑 YAML 文本。导入后按分区修改开关、端口、DNS、TUN、节点、代理组、规则以及任意扩展字段。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("打开 YAML 配置") }
        OutlinedButton(onClick = onImportClashMi, modifier = Modifier.fillMaxWidth()) {
            Text("导入 ClashMi 备份 ZIP")
        }
        InfoCard(
            title = "ClashMi 正确导入方式",
            body = "在 ClashMi 打开“设置 → 备份与同步 → 导出”，然后分享到本应用；也可以保存 backup.zip 后点击上面的按钮。应用会列出备份里的全部配置。",
        )
        InfoCard(
            title = "为什么文件选择器里看不到",
            body = "ClashMi 把 profiles 配置放在 Android 私有应用目录，系统禁止其他普通应用直接浏览。导出的备份 ZIP 是无需 root 的可靠入口。",
        )
        InfoCard(
            title = "自动优选线路",
            body = "规则向导会创建 url-test 组，按健康检查延迟自动选择线路，并把包名规则插到兜底规则之前。",
        )
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverviewView(
    state: EditorState,
    onValidate: () -> Unit,
    onSaveAndShare: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ValidationBanner(state.parseError, onValidate)
        }
        item {
            Button(
                onClick = onSaveAndShare,
                enabled = state.parseError == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("保存并分享到 Clash")
            }
        }
        item {
            Text("配置概览", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        item { MetricGrid(state.summary) }
        item {
            Text("规则组", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        }
        if (state.summary.groups.isEmpty()) {
            item { InfoCard("没有规则组", "请先在配置中添加 proxy-groups。") }
        } else {
            items(state.summary.groups, key = { it.name }) { group -> GroupCard(group) }
        }
    }
}

@Composable
private fun MetricGrid(summary: ConfigSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("节点", summary.proxyCount.toString(), Modifier.weight(1f))
            Metric("规则组", summary.groupCount.toString(), Modifier.weight(1f))
            Metric("规则", summary.ruleCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("代理提供者", summary.proxyProviderCount.toString(), Modifier.weight(1f))
            Metric("规则提供者", summary.ruleProviderCount.toString(), Modifier.weight(1f))
            Metric("DNS", if (summary.dnsEnabled) "已开启" else "未开启", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GroupCard(group: GroupInfo) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${group.type.ifBlank { "未知类型" }} · ${group.proxies.size} 个条目 · ${group.providers.size} 个提供者",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ValidationBanner(error: String?, onValidate: () -> Unit) {
    val isError = error != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onValidate),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(if (isError) "配置结构需要修正" else "配置结构有效", fontWeight = FontWeight.SemiBold)
            Text(
                error ?: "GUI 修改会实时更新结构；保存前也会自动校验。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ImportProfileDialog(
    profiles: List<String>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 ClashMi 配置") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(profiles) { index, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        Text("导入", color = MaterialTheme.colorScheme.primary)
                    }
                    if (index != profiles.lastIndex) androidx.compose.material3.HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RuleWizardView(
    state: EditorState,
    onAddCustomRule: (CustomRuleRequest) -> RuleAddResult,
    onRemoveRule: (Int) -> Unit,
    onApply: (RuleWizardRequest) -> Unit,
) {
    val groups = state.summary.groups
    var packageNames by rememberSaveable { mutableStateOf("") }
    var domains by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("应用 · 自动优选") }
    var parentName by rememberSaveable { mutableStateOf(groups.firstOrNull()?.name.orEmpty()) }
    var includeProviders by rememberSaveable { mutableStateOf(true) }
    var providerFilter by rememberSaveable { mutableStateOf("") }
    var groupType by rememberSaveable { mutableStateOf("url-test") }
    var testUrl by rememberSaveable { mutableStateOf("https://www.gstatic.com/generate_204") }
    var interval by rememberSaveable { mutableStateOf("300") }
    var tolerance by rememberSaveable { mutableStateOf("80") }
    var forceProcessLookup by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var ruleSearch by rememberSaveable { mutableStateOf("") }
    var ruleStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var showCustomRuleDialog by rememberSaveable { mutableStateOf(false) }
    var visibleRuleLimit by rememberSaveable { mutableStateOf(100) }
    val selected = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val parent = groups.firstOrNull { it.name == parentName }
    val candidates = parent?.proxies.orEmpty().filterNot { it in setOf("DIRECT", "REJECT", "COMPATIBLE") }
    val rules = state.root["rules"] as? List<*> ?: emptyList<Any?>()
    val filteredRules = remember(rules, ruleSearch, visibleRuleLimit) {
        val query = ruleSearch.trim()
        rules.asSequence()
            .mapIndexed { index, value -> index to value.toString() }
            .filter { query.isEmpty() || it.second.contains(query, ignoreCase = true) }
            .take(visibleRuleLimit)
            .toList()
    }

    LaunchedEffect(groups) {
        if (groups.none { it.name == parentName }) parentName = groups.firstOrNull()?.name.orEmpty()
    }
    LaunchedEffect(parentName) {
        selected.clear()
        selected.addAll(candidates)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("分流规则", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "搜索现有规则，或按类型、匹配内容和策略组添加自定义分流。新规则会自动插入 MATCH 之前。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ruleSearch,
                    onValueChange = {
                        ruleSearch = it
                        visibleRuleLimit = 100
                        ruleStatus = null
                    },
                    label = { Text("搜索分流规则") },
                    supportingText = { Text("可搜索域名、包名、规则类型或策略组") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = { showCustomRuleDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("添加自定义分流规则")
                }
                ruleStatus?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (ruleSearch.isBlank()) "共 ${rules.size} 条，当前显示 ${filteredRules.size} 条"
                    else "找到 ${filteredRules.size} 条匹配结果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (filteredRules.isEmpty()) {
            item { InfoCard("没有匹配规则", if (rules.isEmpty()) "当前配置还没有 rules。" else "请更换搜索关键词。") }
        } else {
            items(filteredRules, key = { "${it.first}:${it.second}" }) { (originalIndex, rule) ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("第 ${originalIndex + 1} 条", style = MaterialTheme.typography.labelMedium)
                            Text(rule, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { onRemoveRule(originalIndex) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (ruleSearch.isBlank() && filteredRules.size < rules.size) {
            item {
                OutlinedButton(
                    onClick = { visibleRuleLimit += 100 },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("再显示 ${minOf(100, rules.size - filteredRules.size)} 条") }
            }
        }
        item {
            androidx.compose.material3.HorizontalDivider()
            Text("应用专属自动线路", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "包名规则只匹配该 App 的连接；域名补充规则会全局匹配同一域名，请谨慎填写。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = packageNames,
                onValueChange = { packageNames = it },
                label = { Text("Android 包名（每行一个）") },
                supportingText = { Text("例：org.telegram.messenger") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("自动选择方式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { groupType = "url-test" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (groupType == "url-test") "✓ 最低延迟" else "最低延迟")
                    }
                    OutlinedButton(
                        onClick = { groupType = "fallback" },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (groupType == "fallback") "✓ 稳定优先" else "稳定优先")
                    }
                }
                Text(
                    if (groupType == "url-test") "url-test 会选择健康检查延迟最低的线路。"
                    else "fallback 会按你下面的线路顺序使用第一个可用节点，超时后自动切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = domains,
                onValueChange = { domains = it },
                label = { Text("补充域名（可选，每行一个）") },
                supportingText = { Text("不需要穷举：包名匹配成功时已覆盖该 App 的所有域名") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        item {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("新建自动规则组名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Column {
                Text("从哪个现有组选择线路", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(parentName.ifBlank { "配置里没有可用规则组" }, modifier = Modifier.weight(1f))
                        Text("▾")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text("${group.name} · ${group.type}") },
                                onClick = { parentName = group.name; menuOpen = false },
                            )
                        }
                    }
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (selected.size == candidates.size) selected.clear()
                            else { selected.clear(); selected.addAll(candidates) }
                        }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = candidates.isNotEmpty() && selected.size == candidates.size,
                            onCheckedChange = null,
                        )
                        Text("选择本组所有配置内线路（${selected.size}/${candidates.size}）")
                    }
                    candidates.forEach { proxy ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (proxy in selected) selected.remove(proxy) else selected.add(proxy)
                            }.padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = proxy in selected, onCheckedChange = null)
                            Text(proxy, maxLines = 1)
                        }
                    }
                    if (candidates.isEmpty()) {
                        Text(
                            "此组没有内联线路；如果它使用 provider，请启用下面的提供者选项。",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            SettingSwitch(
                title = "复制上级组的代理提供者",
                subtitle = "适用于 proxy-providers；要自动测速，provider 自身也应启用 health-check",
                checked = includeProviders,
                onCheckedChange = { includeProviders = it },
            )
        }
        if (includeProviders && parent?.providers?.isNotEmpty() == true) {
            item {
                OutlinedTextField(
                    value = providerFilter,
                    onValueChange = { providerFilter = it },
                    label = { Text("Provider 节点筛选正则（可选）") },
                    supportingText = { Text("例：(?i)港|HK|Hong Kong") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
        item {
            SettingSwitch(
                title = "强制识别进程/包名",
                subtitle = "写入 find-process-mode: always；包名路由更可靠，但会增加少量识别开销",
                checked = forceProcessLookup,
                onCheckedChange = { forceProcessLookup = it },
            )
        }
        item {
            OutlinedTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                label = { Text("健康检查地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit) },
                    label = { Text("间隔（秒）") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (groupType == "url-test") {
                    OutlinedTextField(
                        value = tolerance,
                        onValueChange = { tolerance = it.filter(Char::isDigit) },
                        label = { Text("容差（ms）") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    onApply(
                        RuleWizardRequest(
                            packageNames = packageNames.lineSequence().toList(),
                            domains = domains.lineSequence().toList(),
                            groupName = groupName,
                            parentGroupName = parentName,
                            selectedProxies = selected.toList(),
                            includeParentProviders = includeProviders,
                            providerFilter = providerFilter,
                            testUrl = testUrl,
                            groupType = groupType,
                            intervalSeconds = interval.toIntOrNull() ?: 300,
                            toleranceMs = tolerance.toIntOrNull() ?: 80,
                            forceProcessLookup = forceProcessLookup,
                        ),
                    )
                },
                enabled = parentName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("生成并插入规则") }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }

    if (showCustomRuleDialog) {
        CustomRuleDialog(
            groups = groups.map { it.name },
            onDismiss = { showCustomRuleDialog = false },
            onConfirm = { request ->
                val result = onAddCustomRule(request)
                ruleSearch = result.rule
                visibleRuleLimit = 100
                ruleStatus = if (result.existed) {
                    "规则已存在，已定位到第 ${result.index + 1} 条"
                } else {
                    "已添加并定位到第 ${result.index + 1} 条"
                }
                showCustomRuleDialog = false
                coroutineScope.launch { listState.animateScrollToItem(2) }
            },
        )
    }
}

@Composable
private fun CustomRuleDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (CustomRuleRequest) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf("DOMAIN-SUFFIX") }
    var customType by rememberSaveable { mutableStateOf("") }
    var payload by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf(groups.firstOrNull() ?: "DIRECT") }
    var noResolve by rememberSaveable { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var targetMenu by remember { mutableStateOf(false) }
    val targets = remember(groups) { (groups + listOf("DIRECT", "REJECT")).distinct() }
    val effectiveType = if (type == CUSTOM_RULE_TYPE) customType.trim().uppercase() else type
    val acceptsNoResolve = effectiveType in setOf("GEOIP", "IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义分流规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("规则类型", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(type, modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            CUSTOM_RULE_TYPES.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        type = option
                                        if (option == "MATCH") payload = ""
                                        typeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (type == CUSTOM_RULE_TYPE) {
                    OutlinedTextField(
                        value = customType,
                        onValueChange = { customType = it.uppercase() },
                        label = { Text("自定义规则类型") },
                        supportingText = { Text("例：DOMAIN-REGEX、SUB-RULE") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                if (effectiveType != "MATCH") {
                    OutlinedTextField(
                        value = payload,
                        onValueChange = { payload = it },
                        label = { Text(customRulePayloadLabel(effectiveType)) },
                        supportingText = { Text(customRulePayloadHint(effectiveType)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Column {
                    Text("目标策略组", style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(target, modifier = Modifier.weight(1f))
                            Text("▾")
                        }
                        DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                            targets.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = { target = option; targetMenu = false },
                                )
                            }
                        }
                    }
                }
                if (acceptsNoResolve) {
                    SettingSwitch(
                        title = "不触发 DNS 解析",
                        subtitle = "在规则末尾添加 no-resolve",
                        checked = noResolve,
                        onCheckedChange = { noResolve = it },
                    )
                }
                Text(
                    if (effectiveType == "MATCH") "$effectiveType,$target"
                    else "${effectiveType.ifBlank { "<规则类型>" }},${payload.ifBlank { "<匹配内容>" }},$target${if (noResolve && acceptsNoResolve) ",no-resolve" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(CustomRuleRequest(effectiveType, payload, target, noResolve && acceptsNoResolve)) },
                enabled = effectiveType.isNotBlank() && target.isNotBlank() &&
                    (effectiveType == "MATCH" || payload.isNotBlank()),
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private val CUSTOM_RULE_TYPES = listOf(
    "DOMAIN",
    "DOMAIN-SUFFIX",
    "DOMAIN-KEYWORD",
    "GEOSITE",
    "GEOIP",
    "IP-CIDR",
    "IP-CIDR6",
    "SRC-IP-CIDR",
    "SRC-PORT",
    "DST-PORT",
    "PROCESS-NAME",
    "PROCESS-PATH",
    "RULE-SET",
    "NETWORK",
    "UID",
    "IN-TYPE",
    "DOMAIN-REGEX",
    "SUB-RULE",
    "MATCH",
    CUSTOM_RULE_TYPE,
)

private const val CUSTOM_RULE_TYPE = "自定义类型…"

private fun customRulePayloadLabel(type: String): String = when (type) {
    "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD" -> "域名或关键词"
    "PROCESS-NAME", "PROCESS-PATH" -> "包名、进程名或路径"
    "IP-CIDR", "IP-CIDR6", "SRC-IP-CIDR" -> "CIDR 地址段"
    "SRC-PORT", "DST-PORT" -> "端口或端口范围"
    "RULE-SET" -> "规则提供者名称"
    "GEOSITE", "GEOIP" -> "Geo 数据标签"
    else -> "匹配内容"
}

private fun customRulePayloadHint(type: String): String = when (type) {
    "DOMAIN-SUFFIX" -> "例：telegram.org"
    "PROCESS-NAME" -> "Android 可填写包名，例如 org.telegram.messenger"
    "IP-CIDR" -> "例：192.168.0.0/16"
    "DST-PORT" -> "例：443 或 8000-9000"
    "RULE-SET" -> "例：reject 或 private"
    else -> "填写该规则类型需要匹配的值"
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
