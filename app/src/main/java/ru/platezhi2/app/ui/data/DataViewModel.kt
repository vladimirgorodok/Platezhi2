package ru.platezhi2.app.ui.data

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.data.cloud.CloudSyncCoordinator
import ru.platezhi2.app.data.settings.AppSettings
import ru.platezhi2.app.data.transfer.BackupFile
import ru.platezhi2.app.reminders.Reminders

class DataViewModel(private val container: AppContainer) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupFile>>(emptyList())
    val backups: StateFlow<List<BackupFile>> = _backups.asStateFlow()

    val cloudFolderUri: StateFlow<String?> = container.settingsRepository.settings
        .map { it.cloudFolderUri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Яндекс Диск ----

    val cloudSettings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val cloudStatus: StateFlow<CloudSyncCoordinator.Status> = container.cloudCoordinator.status

    /** Поля ввода держим отдельно, чтобы ввод не «дёргался» при сохранении. */
    private val _login = MutableStateFlow("")
    val login: StateFlow<String> = _login.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private var fieldsLoaded = false
    private var loginSave: Job? = null
    private var passwordSave: Job? = null

    init {
        viewModelScope.launch {
            val current = container.settingsRepository.settings.first()
            if (!fieldsLoaded) {
                _login.value = current.yandexLogin
                _password.value = current.yandexPassword
                fieldsLoaded = true
            }
        }
    }

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
                    if (count > 0) rescheduleReminders()
                }
                .onFailure { _message.value = "Ошибка импорта: ${it.message}" }
            _busy.value = false
        }
    }

    /** Показать список резервных копий в облачной папке. */
    fun loadBackups() {
        val tree = cloudFolderUri.value
        if (tree == null) {
            _message.value = "Сначала выберите облачную папку"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.transferManager.listBackups(Uri.parse(tree)) }
                .onSuccess { list ->
                    _backups.value = list
                    if (list.isEmpty()) _message.value = "В папке нет резервных копий"
                }
                .onFailure { _message.value = "Не удалось прочитать папку: ${it.message}" }
            _busy.value = false
        }
    }

    fun dismissBackups() {
        _backups.value = emptyList()
    }

    fun importBackup(backup: BackupFile, replaceAll: Boolean) {
        _backups.value = emptyList()
        importFile(backup.uri, replaceAll)
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
            _backups.value = emptyList()
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

    // ------------------------------------------------------------------
    // Яндекс Диск
    // ------------------------------------------------------------------

    fun setLogin(value: String) {
        _login.value = value
        loginSave?.cancel()
        loginSave = viewModelScope.launch {
            delay(500)
            container.settingsRepository.setYandexLogin(value)
        }
    }

    fun setPassword(value: String) {
        _password.value = value
        passwordSave?.cancel()
        passwordSave = viewModelScope.launch {
            delay(500)
            container.settingsRepository.setYandexPassword(value)
        }
    }

    fun flushCredentials() {
        loginSave?.cancel()
        passwordSave?.cancel()
        viewModelScope.launch {
            container.settingsRepository.setYandexLogin(_login.value)
            container.settingsRepository.setYandexPassword(_password.value)
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAutoSync(enabled)
            if (!enabled) return@launch
            // При включении: если на устройстве уже есть платежи — считаем их свежими
            // и при следующем запуске выгрузим. Если данных нет — сначала загрузим из облака.
            val hasLocalData = container.paymentRepository.getCards().isNotEmpty()
            container.settingsRepository.setCloudDirty(hasLocalData)
        }
    }

    fun setKeepCopies(count: Int) {
        viewModelScope.launch { container.settingsRepository.setCloudKeepCopies(count) }
    }

    fun checkCloudConnection() {
        val loginValue = _login.value
        val passwordValue = _password.value
        val folder = cloudSettings.value.cloudFolder
        viewModelScope.launch {
            container.settingsRepository.setYandexLogin(loginValue)
            container.settingsRepository.setYandexPassword(passwordValue)
            val result = container.cloudCoordinator.checkConnection(loginValue, passwordValue, folder)
            result.onSuccess { _message.value = it }
                .onFailure { _message.value = it.message ?: "Не удалось подключиться" }
        }
    }

    /** Список копий платежей в облаке (для диалога «что загружать»). */
    private val _cloudSnapshots = MutableStateFlow(emptyList<ru.platezhi2.app.data.cloud.RemoteEntry>())
    val cloudSnapshots: StateFlow<List<ru.platezhi2.app.data.cloud.RemoteEntry>> =
        _cloudSnapshots.asStateFlow()

    fun loadCloudSnapshots() {
        viewModelScope.launch {
            val result = container.cloudCoordinator.loadSnapshots()
            result.onSuccess { list ->
                _cloudSnapshots.value = list
                if (list.isEmpty()) _message.value = "В облачной папке нет копий"
            }.onFailure { _message.value = it.message ?: "Не удалось прочитать папку" }
        }
    }

    fun dismissCloudSnapshots() {
        _cloudSnapshots.value = emptyList()
    }

    fun pushToCloud() {
        viewModelScope.launch {
            container.cloudCoordinator.manualPush()
                .onSuccess { _message.value = "Данные и значки сохранены в облако" }
                .onFailure { _message.value = it.message ?: "Не удалось сохранить в облако" }
        }
    }

    fun pullFromCloud() {
        viewModelScope.launch {
            _busy.value = true
            container.cloudCoordinator.manualPull()
                .onSuccess { rescheduleReminders() }
            _busy.value = false
        }
    }

    fun pushIconsToCloud() {
        viewModelScope.launch { container.cloudCoordinator.manualIconsPush() }
    }

    fun restoreIconsFromCloud() {
        viewModelScope.launch { container.cloudCoordinator.manualIconsRestore() }
    }

    private suspend fun rescheduleReminders() {
        val cards = container.paymentRepository.getCards()
        val payments = container.paymentRepository.getPayments()
        Reminders.rescheduleAll(container.appContext, cards, payments)
    }
}
