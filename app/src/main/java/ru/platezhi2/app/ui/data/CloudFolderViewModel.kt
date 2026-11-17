package ru.platezhi2.app.ui.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.cloud.CloudException
import ru.platezhi2.app.data.cloud.RemoteEntry
import ru.platezhi2.app.data.settings.normalizeCloudFolder

/** Выбор папки на Яндекс Диске. */
class CloudFolderViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val path: String = "",
        val folders: List<RemoteEntry> = emptyList(),
        val snapshotCount: Int = 0,
        val iconArchiveCount: Int = 0,
        val loading: Boolean = false,
        val error: String? = null,
    ) {
        val parent: String?
            get() = if (path.isEmpty() || path == "/") null
            else normalizeCloudFolder(path.substringBeforeLast('/'))
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            open(settings.cloudFolder)
        }
    }

    fun open(path: String) {
        val target = normalizeCloudFolder(path)
        viewModelScope.launch {
            _state.value = _state.value.copy(path = target, loading = true, error = null)
            runCatching { container.cloudSync.listFolders(target) }
                .onSuccess { folders ->
                    val copies = runCatching { container.cloudSync.listSnapshots(target).size }.getOrDefault(0)
                    val icons = runCatching { container.cloudSync.listIconArchives(target).size }.getOrDefault(0)
                    _state.value = _state.value.copy(
                        folders = folders,
                        snapshotCount = copies,
                        iconArchiveCount = icons,
                        loading = false,
                    )
                }
                .onFailure { error ->
                    // Папки ещё может не быть на Диске — это не ошибка:
                    // она создастся при первой выгрузке.
                    val missing = (error as? CloudException)?.code == 404
                    _state.value = _state.value.copy(
                        folders = emptyList(),
                        snapshotCount = 0,
                        iconArchiveCount = 0,
                        loading = false,
                        error = if (missing) null else error.message ?: "Не удалось прочитать папку",
                    )
                }
        }
    }

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val parent = _state.value.path
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { container.cloudSync.createFolder(if (parent == "/") "/$clean" else "$parent/$clean") }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Не удалось создать папку") }
            open(if (parent == "/") "/$clean" else "$parent/$clean")
        }
    }

    /** Выбрать текущую папку как папку синхронизации. */
    fun choose() {
        val target = _state.value.path
        viewModelScope.launch { container.settingsRepository.setYandexFolder(target) }
    }
}
