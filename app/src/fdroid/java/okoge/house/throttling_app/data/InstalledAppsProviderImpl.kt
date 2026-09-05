package okoge.house.throttling_app.data

import android.content.Context
import android.content.pm.PackageManager

/** fdroid flavor: QUERY_ALL_PACKAGES is declared, so this can list every installed app. */
class InstalledAppsProviderImpl(private val context: Context) : InstalledAppsProvider {
    override fun listInstalledApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, packageManager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
