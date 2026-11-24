package cl.gakuto.kizuna

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import java.io.File
import cl.gakuto.kizuna.data.AppPreferences
import cl.gakuto.kizuna.data.AppThemeMode
import cl.gakuto.kizuna.data.LegacyDataMigrator
import cl.gakuto.kizuna.data.OzDatabase
import cl.gakuto.kizuna.data.SeedLoader
import cl.gakuto.kizuna.launch.SessionCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OzApplication : Application(), SingletonImageLoader.Factory {
    val database by lazy { OzDatabase.get(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "oz_cover_cache"))
                .maxSizeBytes(80L * 1024L * 1024L)
                .build()
        }
        .crossfade(160)
        .build()

    override fun onCreate() {
        super.onCreate()
        val themeMode = AppPreferences(this).appThemeMode
        AppCompatDelegate.setDefaultNightMode(
            when (themeMode) {
                AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            },
        )
        appScope.launch {
            SeedLoader(this@OzApplication, database).seedIfNeeded()
            LegacyDataMigrator(this@OzApplication, database).migrateIfNeeded()
            val upgrades = getSharedPreferences(UPGRADE_PREFS, MODE_PRIVATE)
            if (upgrades.getInt(KEY_GBA_SEMANTIC_REPAIR, 0) < GBA_SEMANTIC_REPAIR_VERSION) {
                database.gameDao().clearLegacyGbaLaunchValidation()
                upgrades.edit().putInt(KEY_GBA_SEMANTIC_REPAIR, GBA_SEMANTIC_REPAIR_VERSION).apply()
            }
        }
        val cleanup = PeriodicWorkRequestBuilder<SessionCleanupWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "oz-session-cleanup", ExistingPeriodicWorkPolicy.KEEP, cleanup
        )
    }

    companion object {
        private const val UPGRADE_PREFS = "oz-data-upgrades"
        private const val KEY_GBA_SEMANTIC_REPAIR = "gba-launch-validation-repair"
        private const val GBA_SEMANTIC_REPAIR_VERSION = 1
    }
}
