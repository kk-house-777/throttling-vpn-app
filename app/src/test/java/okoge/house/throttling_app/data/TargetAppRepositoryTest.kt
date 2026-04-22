package okoge.house.throttling_app.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TargetAppRepositoryTest {

    private lateinit var context: Application
    private lateinit var repository: TargetAppRepository

    private val defaultApp = "okoge.house.throttling_app.testapp"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.dataStore.edit { it.clear() } }
        repository = TargetAppRepository(context)
    }

    // ── initial state ──

    @Test
    fun initialState_returnsDefaultApps() = runTest {
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp), apps)
    }

    // ── addApp ──

    @Test
    fun addApp_addsPackageToSet() = runTest {
        repository.addApp("com.example.app")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun addApp_duplicate_doesNotCreateDuplicate() = runTest {
        repository.addApp("com.example.app")
        repository.addApp("com.example.app")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun addApp_multiplePackages_allPresent() = runTest {
        repository.addApp("com.example.a")
        repository.addApp("com.example.b")
        repository.addApp("com.example.c")
        val apps = repository.targetApps.first()
        assertEquals(
            setOf(defaultApp, "com.example.a", "com.example.b", "com.example.c"),
            apps,
        )
    }

    // ── removeApp ──

    @Test
    fun removeApp_removesPackageFromSet() = runTest {
        repository.addApp("com.example.a")
        repository.addApp("com.example.b")
        repository.removeApp("com.example.a")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.b"), apps)
    }

    @Test
    fun removeApp_nonExistentPackage_isNoOp() = runTest {
        repository.addApp("com.example.app")
        repository.removeApp("com.example.nonexistent")
        val apps = repository.targetApps.first()
        assertEquals(setOf(defaultApp, "com.example.app"), apps)
    }

    @Test
    fun removeApp_onEmptyDataStore_isNoOp() = runTest {
        repository.removeApp("com.example.app")
        val apps = repository.targetApps.first()
        assertTrue(apps.isEmpty())
    }

    @Test
    fun removeApp_defaultApp_removesIt() = runTest {
        repository.addApp(defaultApp)
        repository.removeApp(defaultApp)
        val apps = repository.targetApps.first()
        assertTrue(apps.isEmpty())
    }
}
