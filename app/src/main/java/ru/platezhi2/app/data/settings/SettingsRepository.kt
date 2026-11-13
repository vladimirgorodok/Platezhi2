package ru.platezhi2.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val cloudFolderUri: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val REMINDER_DAYS = intPreferencesKey("reminder_days")
        val CLOUD_FOLDER = stringPreferencesKey("cloud_folder")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = ThemeMode.fromKey(p[Keys.THEME_MODE]),
            themeStyle = ThemeStyle.fromKey(p[Keys.THEME_STYLE]),
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            defaultReminderDaysBefore = p[Keys.REMINDER_DAYS] ?: 1,
            cloudFolderUri = p[Keys.CLOUD_FOLDER],
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

    suspend fun setDefaultReminderDaysBefore(days: Int) {
        context.dataStore.edit { it[Keys.REMINDER_DAYS] = days }
    }

    suspend fun setCloudFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.CLOUD_FOLDER) else prefs[Keys.CLOUD_FOLDER] = uri
        }
    }
}
