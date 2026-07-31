package com.recode.clashcraft.ui

import android.graphics.Typeface
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.recode.clashcraft.MainViewModel
import com.recode.clashcraft.data.ConfigSummary
import com.recode.clashcraft.data.EditorState
import com.recode.clashcraft.data.GroupInfo
import com.recode.clashcraft.data.RuleWizardRequest
import com.recode.clashcraft.ui.theme.ClashCraftTheme

private enum class AppPage(val title: String, val mark: String) {
    OVERVIEW("概览", "◫"),
    EDITOR("编辑", "≡"),
    WIZARD("规则向导", "⌁"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashCraftApp(
    viewModel: MainViewModel,
    onOpen: () -> Unit,
    onSaveAs: (String) -> Unit,
    onSaveAndShare: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(AppPage.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }

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
                    state.text.isBlank() -> WelcomeView(onOpen)
                    page == AppPage.OVERVIEW -> OverviewView(
                        state = state,
                        onValidate = viewModel::validate,
                        onSaveAndShare = { onSaveAndShare(state.fileName) },
                    )
                    page == AppPage.EDITOR -> EditorView(state, viewModel::updateText, viewModel::validate)
                    else -> RuleWizardView(
                        state = state,
                        onApply = {
                            if (viewModel.applyWizard(it)) page = AppPage.EDITOR
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
private fun WelcomeView(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("安全地修改完整 YAML", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "从 ClashMi、Clash for Android、Mihomo Party 等客户端导出或分享配置到这里。应用不会申请全盘存储权限，也不会上传配置。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("选择 YAML 配置文件") }
        InfoCard(
            title = "支持全部配置项",
            body = "原始 YAML 编辑器不会限制键名；常用项提供结构化概览，未来新增字段也能保留。规则向导会校验 YAML 后再修改。",
        )
        InfoCard(
            title = "直接读取的边界",
            body = "Android 不允许普通应用读取其他 App 的私有目录。请在原客户端中导出/分享配置，或用系统文件选择器授权包含配置的目录。",
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
            Text(if (isError) "YAML 需要修正" else "YAML 结构有效", fontWeight = FontWeight.SemiBold)
            Text(
                error ?: "点击重新校验；保存前也会自动校验。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EditorView(state: EditorState, onTextChanged: (String) -> Unit, onValidate: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ValidationBanner(state.parseError, onValidate)
        YamlEditText(
            value = state.text,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun YamlEditText(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val latestCallback by rememberUpdatedState(onValueChange)

    AndroidView(
        modifier = modifier.background(Color(backgroundColor)),
        factory = {
            EditText(context).apply {
                setText(value)
                setSelection(value.length)
                typeface = Typeface.MONOSPACE
                textSize = 13.5f
                setTextColor(textColor)
                setBackgroundColor(backgroundColor)
                setPadding(18, 14, 18, 24)
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setHorizontallyScrolling(true)
                isVerticalScrollBarEnabled = true
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        latestCallback(s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: android.text.Editable?) = Unit
                })
            }
        },
        update = { editor ->
            editor.setTextColor(textColor)
            editor.setBackgroundColor(backgroundColor)
            if (editor.text.toString() != value) {
                val cursor = editor.selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
                editor.setText(value)
                editor.setSelection(cursor)
            }
        },
    )
}

@Composable
private fun RuleWizardView(state: EditorState, onApply: (RuleWizardRequest) -> Unit) {
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
    val selected = remember { mutableStateListOf<String>() }
    val parent = groups.firstOrNull { it.name == parentName }
    val candidates = parent?.proxies.orEmpty().filterNot { it in setOf("DIRECT", "REJECT", "COMPATIBLE") }

    LaunchedEffect(groups) {
        if (groups.none { it.name == parentName }) parentName = groups.firstOrNull()?.name.orEmpty()
    }
    LaunchedEffect(parentName) {
        selected.clear()
        selected.addAll(candidates)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
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
