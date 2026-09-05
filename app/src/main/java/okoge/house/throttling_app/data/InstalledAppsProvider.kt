package okoge.house.throttling_app.data

data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * Lists installed apps by name for the target-app picker. Implemented per
 * product flavor: the fdroid flavor declares QUERY_ALL_PACKAGES and returns
 * the real list; the play flavor returns nothing so the UI falls back to
 * manual package-name entry only.
 */
interface InstalledAppsProvider {
    fun listInstalledApps(): List<InstalledApp>
}
