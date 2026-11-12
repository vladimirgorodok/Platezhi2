package ru.platezhi2.app.ui.data

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer

class DataViewModel(private val container: AppContainer) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val cloudFolderUri: StateFlow<String?> = container.settingsRepository.settings
        .map { it.cloudFolderUri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clearMessage() {
        _message.value = null
    }

    fun exportJson(uri: Uri) = runExport("JSON") { container.transferManager.exportJson(uri) }

    fun exportCsv(uri: Uri) = runExport("CSV") { container.transferManager.exportCsv(uri) }

    private fun runExport(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onSuccess { _message.value = "Экспорт ($label) выполнен" }
                .onFailure { _message.value = "Ошибка экспорта: ${it.message}" }
            _busy.value = false
        }
    }

    fun importFile(uri: Uri, replaceAll: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.transferManager.importFrom(uri, replaceAll) }
                .onSuccess { count ->
                    _message.value = if (count > 0) "Импортировано записей: $count"
                    else "В файле не найдено платежей"
                }
                .onFailure { _message.value = "Ошибка импорта: ${it.message}" }
            _busy.value = false
        }
    }

    fun setCloudFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                container.appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            container.settingsRepository.setCloudFolderUri(uri.toString())
            _message.value = "Папка для резервных копий сохранена"
        }
    }

    fun clearCloudFolder() {
        viewModelScope.launch {
            container.settingsRepository.setCloudFolderUri(null)
            _message.value = "Папка отвязана"
        }
    }

    fun exportToCloud(json: Boolean) {
        val tree = cloudFolderUri.value ?: return
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.transferManager.exportToFolder(Uri.parse(tree), json) }
                .onSuccess { uri ->
                    _message.value = if (uri != null) "Резервная копия сохранена в облаке"
                    else "Не удалось создать файл в папке"
                }
                .onFailure { _message.value = "Ошибка экспорта: ${it.message}" }
            _busy.value = false
        }
    }
}
