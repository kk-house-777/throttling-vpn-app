package okoge.house.throttling_app.data

import android.content.Context

/** Play flavor: no QUERY_ALL_PACKAGES, so the app-name picker stays unavailable. */
class InstalledAppsProviderImpl(private val context: Context) : InstalledAppsProvider {
    override fun listInstalledApps(): List<InstalledApp> = emptyList()
}
