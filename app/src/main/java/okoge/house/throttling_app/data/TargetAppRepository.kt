package okoge.house.throttling_app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okoge.house.throttling_app.BuildConfig


private val Context.dataStore by preferencesDataStore(name = "target_apps")

class TargetAppRepository(private val context: Context) {

    private val key = stringSetPreferencesKey("target_app_ids")

    private val defaultApps: Set<String> =
        if (BuildConfig.DEBUG) setOf("okoge.house.throttling_app.testapp") else emptySet()

    val targetApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[key] ?: defaultApps
    }

    suspend fun addApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: defaultApps
            prefs[key] = current + packageName
        }
    }

    suspend fun removeApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = current - packageName
        }
    }
}
