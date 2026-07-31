package com.recode.clashcraft

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recode.clashcraft.data.ClashShareRequest
import com.recode.clashcraft.data.ConfigPath
import com.recode.clashcraft.data.ConfigRepository
import com.recode.clashcraft.data.ConfigTree
import com.recode.clashcraft.data.ConfigValueType
import com.recode.clashcraft.data.CustomRuleRequest
import com.recode.clashcraft.data.CustomRuleManager
import com.recode.clashcraft.data.EditorState
import com.recode.clashcraft.data.ImportedProfile
import com.recode.clashcraft.data.MihomoYaml
import com.recode.clashcraft.data.RuleWizard
import com.recode.clashcraft.data.RuleAddResult
import com.recode.clashcraft.data.RuleWizardRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(application)
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private val _shareRequests = MutableSharedFlow<ClashShareRequest>(extraBufferCapacity = 1)
    val shareRequests: SharedFlow<ClashShareRequest> = _shareRequests.asSharedFlow()
    private var summaryJob: Job? = null

    fun open(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { repository.read(uri) }
                .onSuccess { result ->
                    if (result.profiles.size == 1) {
                        loadProfile(
                            profile = result.profiles.single(),
                            uri = uri.takeUnless { result.isArchive },
                            message = if (result.isArchive) "已从 ClashMi 备份导入配置" else null,
                        )
                    } else {
                        _state.update {
                            it.copy(
                                isBusy = false,
                                pendingImports = result.profiles,
                                message = "ClashMi 备份中有 ${result.profiles.size} 个配置，请选择一个",
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, message = error.userMessage()) }
                }
        }
    }

    fun openSharedText(text: String) {
        loadProfile(
            profile = ImportedProfile("shared-config.yaml", "分享的配置", text),
            uri = null,
            message = "已读取分享的配置",
        )
    }

    fun selectImportedProfile(index: Int) {
        val profile = _state.value.pendingImports.getOrNull(index) ?: return
        loadProfile(profile, uri = null, message = "已从 ClashMi 备份导入 ${profile.displayName}")
    }

    fun dismissImportedProfiles() {
        _state.update { it.copy(pendingImports = emptyList()) }
    }

    fun setConfigValue(path: ConfigPath, value: Any?) {
        editTree { ConfigTree.set(it, path, value) }
    }

    fun removeConfigValue(path: ConfigPath) {
        editTree { ConfigTree.remove(it, path) }
    }

    fun renameConfigKey(parentPath: ConfigPath, oldKey: String, newKey: String) {
        editTree { ConfigTree.renameKey(it, parentPath, oldKey, newKey) }
    }

    fun addConfigMapEntry(path: ConfigPath, key: String, type: ConfigValueType) {
        editTree { ConfigTree.addMapEntry(it, path, key, type) }
    }

    fun addConfigListItem(path: ConfigPath, type: ConfigValueType) {
        editTree { ConfigTree.addListItem(it, path, type) }
    }

    fun moveConfigListItem(path: ConfigPath, index: Int, direction: Int) {
        editTree { ConfigTree.moveListItem(it, path, index, direction) }
    }

    fun addCustomRule(request: CustomRuleRequest): RuleAddResult {
        val currentRules = (_state.value.root["rules"] as? List<*>)?.map { it.toString() }.orEmpty()
        val (updatedRules, result) = CustomRuleManager.insert(currentRules, request)
        if (result.existed) {
            _state.update { it.copy(message = "该分流规则已存在，已定位到第 ${result.index + 1} 条") }
            return result
        }
        val currentRoot = _state.value.root
        val updatedRoot = if ("rules" in currentRoot) {
            ConfigTree.set(currentRoot, ConfigPath.Root.key("rules"), updatedRules)
        } else {
            ConfigTree.addMapEntry(currentRoot, ConfigPath.Root, "rules", ConfigValueType.LIST).let {
                ConfigTree.set(it, ConfigPath.Root.key("rules"), updatedRules)
            }
        }
        applyEditedRoot(updatedRoot, "已添加自定义分流规则")
        return result
    }

    fun removeRule(index: Int) {
        removeConfigValue(ConfigPath.Root.key("rules").index(index))
    }

    fun validate() {
        val root = _state.value.root
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { MihomoYaml.summarize(root) } }
            if (_state.value.root !== root) return@launch
            _state.update {
                it.copy(
                    summary = result.getOrDefault(it.summary),
                    parseError = result.exceptionOrNull()?.message,
                    message = if (result.isSuccess) "配置结构有效" else "发现配置结构错误",
                )
            }
        }
    }

    fun save() {
        val uri = _state.value.uri ?: run {
            _state.update { it.copy(message = "请选择“另存为”") }
            return
        }
        saveTo(uri, _state.value.fileName)
    }

    fun saveAs(uri: Uri) {
        saveTo(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "config.yaml")
    }

    fun saveAndShare() {
        val uri = _state.value.uri ?: run {
            _state.update { it.copy(message = "请先选择保存位置") }
            return
        }
        saveTo(uri, _state.value.fileName, shareAfterSave = true)
    }

    fun saveAsAndShare(uri: Uri) {
        saveTo(
            uri = uri,
            displayName = uri.lastPathSegment?.substringAfterLast('/') ?: _state.value.fileName,
            shareAfterSave = true,
        )
    }

    fun applyWizard(request: RuleWizardRequest, onApplied: () -> Unit) {
        val rootSnapshot = _state.value.root
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val generated = RuleWizard.apply(MihomoYaml.dump(rootSnapshot), request)
                    val root = MihomoYaml.parse(generated)
                    Triple(generated, root, MihomoYaml.summarize(root))
                }
            }
            result.onSuccess { (generated, root, summary) ->
                _state.update {
                    it.copy(
                        text = generated,
                        root = root,
                        summary = summary,
                        parseError = null,
                        isDirty = true,
                        isBusy = false,
                        message = "规则已生成，请检查后保存",
                    )
                }
                onApplied()
            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, message = error.userMessage()) }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun saveTo(uri: Uri, displayName: String, shareAfterSave: Boolean = false) {
        viewModelScope.launch {
            val root = _state.value.root
            val summary = withContext(Dispatchers.Default) { runCatching { MihomoYaml.summarize(root) } }
            if (summary.isFailure) {
                _state.update { it.copy(parseError = summary.exceptionOrNull()?.message, message = "配置结构有错误，未保存") }
                return@launch
            }
            _state.update { it.copy(isBusy = true, message = null) }
            try {
                val text = withContext(Dispatchers.Default) { MihomoYaml.dump(root) }
                repository.write(uri, text)
                val finalName = if (displayName.isBlank()) "config.yaml" else displayName
                val shareUri = if (shareAfterSave) repository.createShareCopy(text, finalName) else null
                _state.update {
                    it.copy(
                        uri = uri,
                        fileName = finalName,
                        text = text,
                        isDirty = false,
                        isBusy = false,
                        summary = summary.getOrThrow(),
                        parseError = null,
                        message = if (shareAfterSave) "保存成功，正在打开 Clash…" else "保存成功",
                    )
                }
                if (shareUri != null) {
                    _shareRequests.emit(ClashShareRequest(shareUri, finalName))
                }
            } catch (error: Exception) {
                _state.update { it.copy(isBusy = false, message = error.userMessage()) }
            }
        }
    }

    private fun loadProfile(profile: ImportedProfile, uri: Uri?, message: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, pendingImports = emptyList(), message = null) }
            val parsed = withContext(Dispatchers.Default) {
                runCatching {
                    val root = MihomoYaml.parse(profile.text)
                    root to MihomoYaml.summarize(root)
                }
            }
            parsed.onSuccess { (root, summary) ->
                _state.value = EditorState(
                    text = profile.text,
                    root = root,
                    uri = uri,
                    fileName = profile.fileName,
                    isDirty = uri == null,
                    summary = summary,
                    message = message ?: "已导入 ${profile.displayName}",
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        parseError = error.userMessage(),
                        message = "无法解析 ${profile.displayName}：${error.userMessage()}",
                    )
                }
            }
        }
    }

    private fun editTree(change: (Map<String, Any?>) -> LinkedHashMap<String, Any?>) {
        runCatching { change(_state.value.root) }
            .onSuccess { root -> applyEditedRoot(root, null) }
            .onFailure { error -> _state.update { it.copy(message = error.userMessage()) } }
    }

    private fun applyEditedRoot(root: Map<String, Any?>, message: String?) {
        _state.update {
            it.copy(
                root = root,
                revision = it.revision + 1,
                isDirty = true,
                parseError = null,
                message = message,
            )
        }
        scheduleSummary(root)
    }

    private fun scheduleSummary(root: Map<String, Any?>) {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            delay(250)
            val summary = withContext(Dispatchers.Default) { runCatching { MihomoYaml.summarize(root) } }
            if (_state.value.root !== root) return@launch
            _state.update {
                it.copy(
                    summary = summary.getOrDefault(it.summary),
                    parseError = summary.exceptionOrNull()?.message,
                )
            }
        }
    }

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "操作失败"

}
