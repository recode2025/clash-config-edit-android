package com.recode.clashcraft

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recode.clashcraft.data.ClashShareRequest
import com.recode.clashcraft.data.ConfigRepository
import com.recode.clashcraft.data.EditorState
import com.recode.clashcraft.data.MihomoYaml
import com.recode.clashcraft.data.RuleWizard
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
    private var validationJob: Job? = null

    fun open(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null) }
            runCatching { repository.read(uri) }
                .onSuccess { (text, name) ->
                    val validation = withContext(Dispatchers.Default) { MihomoYaml.validate(text) }
                    _state.value = EditorState(
                        text = text,
                        uri = uri,
                        fileName = name,
                        isDirty = false,
                        isBusy = false,
                        parseError = validation.exceptionOrNull()?.message,
                        summary = validation.getOrDefault(com.recode.clashcraft.data.ConfigSummary()),
                        message = "已导入 $name",
                    )
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, message = error.userMessage()) }
                }
        }
    }

    fun openSharedText(text: String) {
        viewModelScope.launch {
            val validation = withContext(Dispatchers.Default) { MihomoYaml.validate(text) }
            _state.value = EditorState(
                text = text,
                fileName = "shared-config.yaml",
                isDirty = true,
                parseError = validation.exceptionOrNull()?.message,
                summary = validation.getOrDefault(com.recode.clashcraft.data.ConfigSummary()),
                message = "已读取分享的文本配置",
            )
        }
    }

    fun updateText(text: String) {
        if (text == _state.value.text) return
        _state.update { it.copy(text = text, isDirty = true, message = null) }
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            delay(450)
            validateNow(showSuccess = false)
        }
    }

    fun validate() {
        viewModelScope.launch { validateNow(showSuccess = true) }
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

    fun applyWizard(request: RuleWizardRequest): Boolean {
        val current = _state.value
        return runCatching { RuleWizard.apply(current.text, request) }
            .onSuccess { generated ->
                val summary = MihomoYaml.validate(generated).getOrThrow()
                _state.update {
                    it.copy(
                        text = generated,
                        summary = summary,
                        parseError = null,
                        isDirty = true,
                        message = "规则已生成，请检查后保存",
                    )
                }
            }
            .onFailure { error -> _state.update { it.copy(message = error.userMessage()) } }
            .isSuccess
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun saveTo(uri: Uri, displayName: String, shareAfterSave: Boolean = false) {
        viewModelScope.launch {
            val text = _state.value.text
            val validation = withContext(Dispatchers.Default) { MihomoYaml.validate(text) }
            if (validation.isFailure) {
                _state.update { it.copy(parseError = validation.exceptionOrNull()?.message, message = "YAML 有错误，未保存") }
                return@launch
            }
            _state.update { it.copy(isBusy = true, message = null) }
            try {
                repository.write(uri, text)
                val finalName = if (displayName.isBlank()) "config.yaml" else displayName
                val shareUri = if (shareAfterSave) repository.createShareCopy(text, finalName) else null
                _state.update {
                    it.copy(
                        uri = uri,
                        fileName = finalName,
                        isDirty = false,
                        isBusy = false,
                        summary = validation.getOrThrow(),
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

    private suspend fun validateNow(showSuccess: Boolean) {
        val snapshot = _state.value.text
        if (snapshot.isBlank()) return
        val result = withContext(Dispatchers.Default) { MihomoYaml.validate(snapshot) }
        if (_state.value.text != snapshot) return
        _state.update {
            it.copy(
                parseError = result.exceptionOrNull()?.message,
                summary = result.getOrDefault(it.summary),
                message = if (showSuccess) {
                    if (result.isSuccess) "YAML 结构有效" else "发现 YAML 错误"
                } else it.message,
            )
        }
    }

    private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "操作失败"
}
