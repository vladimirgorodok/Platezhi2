package ru.platezhi2.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val key: String, val title: String) {
    SYSTEM("system", "Как в системе"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная");

    companion object {
        fun fromKey(k: String?): ThemeMode = entries.firstOrNull { it.key == k } ?: SYSTEM
    }
}

/** Цветовой стиль оформления: акцент и оттенки фона. */
enum class ThemeStyle(val key: String, val title: String) {
    CLASSIC("classic", "Классика"),
    OCEAN("ocean", "Океан"),
    SUNSET("sunset", "Закат"),
    FOREST("forest", "Лес");

    companion object {
        fun fromKey(k: String?): ThemeStyle = entries.firstOrNull { it.key == k } ?: CLASSIC
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.CLASSIC,
    val dynamicColor: Boolean = false,
    val defaultReminderDaysBefore: Int = 1,
    /** Прозрачность иконки-подложки в строке платежа, проценты (90 — почти не видно). */
    val backdropTransparency: Int = 90,

    // ---- Яндекс Диск (синхронизация между устройствами) ----
    val yandexLogin: String = "",
    val yandexPassword: String = "",
    /** Папка на Яндекс Диске, например «/Платежи2». */
    val cloudFolder: String = DEFAULT_CLOUD_FOLDER,
    val autoSync: Boolean = false,
    /** Сколько последних копий хранить в облачной папке. */
    val cloudKeepCopies: Int = 5,
    val cloudLastSyncAt: Long = 0L,
    /** Имя файла, который это устройство записало или прочитало последним. */
    val cloudLastFileName: String = "",
    /** Есть несохранённые в облако изменения (в том числе сделанные без интернета). */
    val cloudDirty: Boolean = false,
    /** Имя архива значков, из которого это устройство уже подтянуло картинки. */
    val cloudLastIconsName: String = "",
) {
    val cloudConfigured: Boolean
        get() = yandexLogin.isNotBlank() && yandexPassword.isNotBlank()
}

const val DEFAULT_CLOUD_FOLDER = "/Платежи2"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val REMINDER_DAYS = intPreferencesKey("reminder_days")
        val BACKDROP = intPreferencesKey("backdrop_transparency")

        val CLOUD_LOGIN = stringPreferencesKey("cloud_login")
        val CLOUD_PASSWORD = stringPreferencesKey("cloud_password")
        val YANDEX_FOLDER = stringPreferencesKey("cloud_yandex_folder")
        val AUTO_SYNC = booleanPreferencesKey("cloud_auto_sync")
        val KEEP_COPIES = intPreferencesKey("cloud_keep_copies")
        val LAST_SYNC_AT = longPreferencesKey("cloud_last_sync_at")
        val LAST_FILE = stringPreferencesKey("cloud_last_file")
        val DIRTY = booleanPreferencesKey("cloud_dirty")
        val LAST_ICONS = stringPreferencesKey("cloud_last_icons")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.fromKey(p[Keys.THEME_MODE]),
            themeStyle = ThemeStyle.fromKey(p[Keys.THEME_STYLE]),
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            defaultReminderDaysBefore = p[Keys.REMINDER_DAYS] ?: 1,
            backdropTransparency = (p[Keys.BACKDROP] ?: 90).coerceIn(0, 100),
            yandexLogin = p[Keys.CLOUD_LOGIN].orEmpty().trim(),
            yandexPassword = p[Keys.CLOUD_PASSWORD].orEmpty(),
            cloudFolder = p[Keys.YANDEX_FOLDER].orEmpty().ifBlank { DEFAULT_CLOUD_FOLDER },
            autoSync = p[Keys.AUTO_SYNC] ?: false,
            cloudKeepCopies = (p[Keys.KEEP_COPIES] ?: 5).coerceIn(1, 50),
            cloudLastSyncAt = p[Keys.LAST_SYNC_AT] ?: 0L,
            cloudLastFileName = p[Keys.LAST_FILE].orEmpty(),
            cloudDirty = p[Keys.DIRTY] ?: false,
            cloudLastIconsName = p[Keys.LAST_ICONS].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { it[Keys.THEME_STYLE] = style.key }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    }

    suspend fun setBackdropTransparency(value: Int) {
        context.dataStore.edit { it[Keys.BACKDROP] = value.coerceIn(0, 100) }
    }

    suspend fun setDefaultReminderDaysBefore(days: Int) {
        context.dataStore.edit { it[Keys.REMINDER_DAYS] = days }
    }

    // ---- Яндекс Диск ----

    suspend fun setYandexLogin(login: String) {
        context.dataStore.edit { it[Keys.CLOUD_LOGIN] = login.trim() }
    }

    suspend fun setYandexPassword(password: String) {
        context.dataStore.edit { it[Keys.CLOUD_PASSWORD] = password.trim() }
    }

    suspend fun setYandexFolder(path: String) {
        context.dataStore.edit { it[Keys.YANDEX_FOLDER] = normalizeCloudFolder(path) }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }

    suspend fun setCloudKeepCopies(count: Int) {
        context.dataStore.edit { it[Keys.KEEP_COPIES] = count.coerceIn(1, 50) }
    }

    /** Состояние синхронизации: отметка времени и имя последней копии. */
    suspend fun setCloudSynced(fileName: String, atMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_FILE] = fileName
            prefs[Keys.LAST_SYNC_AT] = atMillis
            prefs[Keys.DIRTY] = false
        }
    }

    suspend fun setCloudDirty(dirty: Boolean) {
        context.dataStore.edit { it[Keys.DIRTY] = dirty }
    }

    /** Запоминает архив значков, из которого картинки уже перенесены на устройство. */
    suspend fun setCloudIconsRestored(archiveName: String) {
        context.dataStore.edit { it[Keys.LAST_ICONS] = archiveName }
    }
}

/** Приводит путь к виду «/Папка/Вложенная»: с ведущим слэшем и без хвостового. */
fun normalizeCloudFolder(path: String): String {
    val trimmed = path.trim().replace('\\', '/')
    val withSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    val collapsed = withSlash.replace(Regex("/{2,}"), "/")
    return collapsed.trimEnd('/').ifBlank { "/" }
}
