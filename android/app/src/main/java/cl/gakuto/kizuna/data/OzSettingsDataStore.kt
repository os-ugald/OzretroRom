package cl.gakuto.kizuna.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Reactive Jetpack DataStore wrapper replacing synchronous SharedPreferences ("oz_retrorom").
 * Automatically migrates existing installs via SharedPreferencesMigration and maintains an
 * in-memory snapshot backed by DataStore<Preferences>.
 */
class OzSettingsDataStore private constructor(
    private val appContext: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotLock = Any()
    private val snapshot = ConcurrentHashMap<String, Any>()
    private val pendingValues = ConcurrentHashMap<String, Any>()
    private val keySeq = ConcurrentHashMap<String, AtomicLong>()
    private val hydrated = CompletableDeferred<Unit>()
    private val pendingWrites = ConcurrentHashMap.newKeySet<Job>()
    private val _stateVersion = MutableStateFlow(0L)
    val stateVersion: StateFlow<Long> = _stateVersion

    init {
        scope.launch {
            runCatching {
                val initial = dataStore.data.first()
                applyPreferencesToSnapshot(initial)
            }
            if (!hydrated.isCompleted) {
                hydrated.complete(Unit)
            }
            dataStore.data.drop(1).collect { prefs ->
                applyPreferencesToSnapshot(prefs)
            }
        }
    }

    private fun nextSeq(key: String): Long =
        keySeq.computeIfAbsent(key) { AtomicLong(0L) }.incrementAndGet()

    private fun currentSeq(key: String): Long =
        keySeq[key]?.get() ?: 0L

    private fun applyPreferencesToSnapshot(prefs: Preferences) {
        synchronized(snapshotLock) {
            snapshot.clear()
            for ((key, value) in prefs.asMap()) {
                snapshot[key.name] = value
            }
            for ((key, pending) in pendingValues) {
                if (pending === DELETED_SENTINEL) {
                    snapshot.remove(key)
                } else {
                    snapshot[key] = pending
                }
            }
            _stateVersion.value = _stateVersion.value + 1L
        }
    }

    private fun ensureHydratedBlocking() {
        if (!hydrated.isCompleted) {
            runBlocking(Dispatchers.IO) {
                hydrated.await()
            }
        }
    }

    suspend fun awaitHydrated() {
        if (!hydrated.isCompleted) {
            hydrated.await()
        }
    }

    val preferencesFlow: Flow<Preferences> = dataStore.data

    fun stringFlow(key: String, defaultValue: String = ""): Flow<String> =
        dataStore.data.map { it[stringPreferencesKey(key)] ?: defaultValue }

    fun booleanFlow(key: String, defaultValue: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: defaultValue }

    fun contains(key: String): Boolean {
        ensureHydratedBlocking()
        return snapshot.containsKey(key)
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        ensureHydratedBlocking()
        return (snapshot[key] as? String) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        ensureHydratedBlocking()
        return (snapshot[key] as? Boolean) ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        ensureHydratedBlocking()
        val v = snapshot[key]
        return when (v) {
            is Int -> v
            is Long -> v.toInt()
            else -> defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        ensureHydratedBlocking()
        val v = snapshot[key]
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            else -> defaultValue
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> {
        ensureHydratedBlocking()
        return (snapshot[key] as? Set<String>)?.toSet() ?: defaultValue
    }

    fun keysStartingWith(prefix: String): Set<String> {
        ensureHydratedBlocking()
        return snapshot.keys.filterTo(linkedSetOf()) { it.startsWith(prefix) }
    }

    fun putString(key: String, value: String?) {
        ensureHydratedBlocking()
        val token = recordPending(key, value)
        editBatch(mapOf(key to token)) { prefs ->
            val pk = stringPreferencesKey(key)
            if (value == null) prefs.remove(pk) else prefs[pk] = value
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        ensureHydratedBlocking()
        val token = recordPending(key, value)
        editBatch(mapOf(key to token)) { it[booleanPreferencesKey(key)] = value }
    }

    fun putInt(key: String, value: Int) {
        ensureHydratedBlocking()
        val token = recordPending(key, value)
        editBatch(mapOf(key to token)) { it[intPreferencesKey(key)] = value }
    }

    fun putLong(key: String, value: Long) {
        ensureHydratedBlocking()
        val token = recordPending(key, value)
        editBatch(mapOf(key to token)) { it[longPreferencesKey(key)] = value }
    }

    fun putStringSet(key: String, value: Set<String>) {
        ensureHydratedBlocking()
        val copy = value.toSet()
        val token = recordPending(key, copy)
        editBatch(mapOf(key to token)) { it[stringSetPreferencesKey(key)] = copy }
    }

    fun removeAll(keys: Collection<String>) {
        if (keys.isEmpty()) return
        ensureHydratedBlocking()
        val tokens = synchronized(snapshotLock) {
            val map = LinkedHashMap<String, Long>(keys.size)
            for (k in keys) {
                snapshot.remove(k)
                pendingValues[k] = DELETED_SENTINEL
                map[k] = nextSeq(k)
            }
            _stateVersion.value = _stateVersion.value + 1L
            map
        }
        editBatch(tokens) { prefs ->
            for (k in keys) {
                prefs.remove(stringPreferencesKey(k))
                prefs.remove(booleanPreferencesKey(k))
                prefs.remove(intPreferencesKey(k))
                prefs.remove(longPreferencesKey(k))
                prefs.remove(stringSetPreferencesKey(k))
            }
        }
    }

    fun updateSync(
        cacheUpdates: Map<String, Any?>,
        datastoreBlock: (MutablePreferences) -> Unit,
    ) {
        ensureHydratedBlocking()
        val tokens = synchronized(snapshotLock) {
            val map = LinkedHashMap<String, Long>(cacheUpdates.size)
            for ((k, v) in cacheUpdates) {
                if (v == null) {
                    snapshot.remove(k)
                    pendingValues[k] = DELETED_SENTINEL
                } else {
                    snapshot[k] = v
                    pendingValues[k] = v
                }
                map[k] = nextSeq(k)
            }
            _stateVersion.value = _stateVersion.value + 1L
            map
        }
        editBatch(tokens, datastoreBlock)
    }

    private fun recordPending(key: String, value: Any?): Long = synchronized(snapshotLock) {
        if (value == null) {
            snapshot.remove(key)
            pendingValues[key] = DELETED_SENTINEL
        } else {
            snapshot[key] = value
            pendingValues[key] = value
        }
        _stateVersion.value = _stateVersion.value + 1L
        nextSeq(key)
    }

    suspend fun flush() {
        awaitHydrated()
        while (pendingWrites.isNotEmpty()) {
            val snapshotJobs = pendingWrites.toList()
            snapshotJobs.forEach { it.join() }
        }
    }

    private fun editBatch(tokens: Map<String, Long>, block: (MutablePreferences) -> Unit) {
        val job = scope.launch {
            awaitHydrated()
            runCatching { dataStore.edit(block) }
            synchronized(snapshotLock) {
                for ((k, token) in tokens) {
                    if (currentSeq(k) == token) {
                        pendingValues.remove(k)
                    }
                }
            }
        }
        pendingWrites.add(job)
        job.invokeOnCompletion { pendingWrites.remove(job) }
    }

    companion object {
        private val DELETED_SENTINEL = Any()
        private const val STORE_FILENAME = "oz_settings.preferences_pb"
        private const val LEGACY_PREFS_NAME = "oz_retrorom"

        private val instances = ConcurrentHashMap<String, OzSettingsDataStore>()

        fun get(context: Context): OzSettingsDataStore {
            val appCtx = context.applicationContext ?: context
            val file = File(appCtx.filesDir, "datastore/$STORE_FILENAME").apply { parentFile?.mkdirs() }
            val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            return instances.getOrPut(key) {
                val store = PreferenceDataStoreFactory.create(
                    migrations = listOf(SharedPreferencesMigration(appCtx, LEGACY_PREFS_NAME)),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { file },
                )
                OzSettingsDataStore(appCtx, store)
            }
        }
    }
}
