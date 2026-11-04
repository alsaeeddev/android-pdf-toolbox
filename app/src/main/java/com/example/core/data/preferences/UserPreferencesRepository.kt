package com.example.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ViewMode {
    /** Scroll the whole document top to bottom. */
    CONTINUOUS_VERTICAL,
    /** One page at a time, swipe right -> left for the next page (default horizontal behaviour). */
    SINGLE_PAGE_HORIZONTAL,
    /** One page at a time, swipe left -> right for the next page. */
    SINGLE_PAGE_HORIZONTAL_REVERSED
}

enum class ReadingMode {
    NORMAL, INVERT_DARK, SEPIA, NIGHT
}

/** A folder the user picked via the system folder picker (Storage Access Framework tree URI). */
data class LibraryFolder(
    val uri: String,
    val name: String
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val defaultViewMode: ViewMode = ViewMode.CONTINUOUS_VERTICAL,
    val keepScreenOn: Boolean = true,
    val readingMode: ReadingMode = ReadingMode.NORMAL,
    val isOnboardingCompleted: Boolean = false
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LIBRARY_FOLDERS = stringPreferencesKey("library_folders")
    }

    /** Folders added to the "All PDFs" library, in the order the user added them. */
    val libraryFoldersFlow: Flow<List<LibraryFolder>> = dataStore.data.map { preferences ->
        parseFolders(preferences[PreferencesKeys.LIBRARY_FOLDERS])
    }

    suspend fun addLibraryFolder(folder: LibraryFolder) {
        dataStore.edit { preferences ->
            val current = parseFolders(preferences[PreferencesKeys.LIBRARY_FOLDERS])
            if (current.none { it.uri == folder.uri }) {
                preferences[PreferencesKeys.LIBRARY_FOLDERS] = serializeFolders(current + folder)
            }
        }
    }

    suspend fun removeLibraryFolder(uri: String) {
        dataStore.edit { preferences ->
            val current = parseFolders(preferences[PreferencesKeys.LIBRARY_FOLDERS])
            preferences[PreferencesKeys.LIBRARY_FOLDERS] =
                serializeFolders(current.filterNot { it.uri == uri })
        }
    }

    private fun parseFolders(raw: String?): List<LibraryFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = obj.optString("uri")
                if (uri.isBlank()) null else LibraryFolder(uri, obj.optString("name", "Folder"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeFolders(folders: List<LibraryFolder>): String {
        val array = JSONArray()
        folders.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name)) }
        return array.toString()
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        val viewModeStr = preferences[PreferencesKeys.DEFAULT_VIEW_MODE] ?: ViewMode.CONTINUOUS_VERTICAL.name
        val viewMode = try {
            ViewMode.valueOf(viewModeStr)
        } catch (_: Exception) {
            ViewMode.CONTINUOUS_VERTICAL
        }

        val readingModeStr = preferences[PreferencesKeys.READING_MODE] ?: ReadingMode.NORMAL.name
        val readingMode = try {
            ReadingMode.valueOf(readingModeStr)
        } catch (_: Exception) {
            ReadingMode.NORMAL
        }

        UserPreferences(
            themeMode = themeMode,
            useDynamicColor = preferences[PreferencesKeys.USE_DYNAMIC_COLOR] ?: false,
            defaultViewMode = viewMode,
            keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true,
            readingMode = readingMode,
            isOnboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setUseDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }

    suspend fun setDefaultViewMode(viewMode: ViewMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_VIEW_MODE] = viewMode.name
        }
    }

    suspend fun setKeepScreenOn(keepScreenOn: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = keepScreenOn
        }
    }

    suspend fun setReadingMode(readingMode: ReadingMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.READING_MODE] = readingMode.name
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }
}
