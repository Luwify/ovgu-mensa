package de.luwify.mensamd.stats

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Why the statistics file could not be used as it is. */
sealed interface StatsProblem {
    /** Written by a newer app version: shown read-only as empty, the file is never touched. */
    data class TooNew(val version: Int) : StatsProblem

    /** Unreadable: moved aside to [keptAs] (never deleted), the statistics start empty. */
    data class Broken(val keptAs: String) : StatsProblem
}

/** The JSON shape shared by the file and the backup. */
internal val statsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

/** Parses a stored statistics document of any older version (migrated on the way). Throws on a newer or broken one. */
internal fun decodeStats(obj: JsonObject): StatsData =
    statsJson.decodeFromJsonElement(StatsData.serializer(), StatsMigrations.migrate(obj))

internal fun encodeStats(data: StatsData): JsonObject =
    statsJson.encodeToJsonElement(StatsData.serializer(), data.copy(version = StatsMigrations.CURRENT)) as JsonObject

/**
 * The statistics as one JSON file ([file], in the app's files dir: not a cache, and excluded from cloud backups like
 * all files). Every change goes through [update] under one lock and is written atomically (temp file, fsync,
 * rename), so a crash leaves either the old or the new file. An older file is migrated and its original kept as
 * `<name>.v<version>`; a newer one is never written ([StatsProblem.TooNew]); a broken one is moved aside.
 */
class StatsStore(
    private val file: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val _data = MutableStateFlow<StatsData?>(null)

    /** Null until loaded (see [load]). */
    val data: StateFlow<StatsData?> = _data.asStateFlow()

    private val _problem = MutableStateFlow<StatsProblem?>(null)
    val problem: StateFlow<StatsProblem?> = _problem.asStateFlow()

    /** Reads the file once; later calls return the loaded data. */
    suspend fun load(): StatsData = lock.withLock { loadLocked() }

    private suspend fun loadLocked(): StatsData {
        _data.value?.let { return it }
        val loaded = withContext(io) { readFile() }
        _data.value = loaded
        return loaded
    }

    private fun readFile(): StatsData {
        if (!file.isFile) return StatsData()
        val text = try {
            file.readText()
        } catch (e: IOException) {
            return broken()
        }
        val obj = try {
            statsJson.parseToJsonElement(text) as? JsonObject ?: return broken()
        } catch (e: Exception) {
            return broken()
        }
        val version = try {
            StatsMigrations.versionOf(obj)
        } catch (e: Exception) {
            return broken()
        }
        if (version > StatsMigrations.CURRENT) {
            _problem.value = StatsProblem.TooNew(version)
            return StatsData()
        }
        val data = try {
            decodeStats(obj)
        } catch (e: Exception) {
            return broken()
        }
        if (version < StatsMigrations.CURRENT) {
            // Keep the original next to the migrated file (never overwrite an earlier copy).
            val keep = File(file.parentFile, "${file.name}.v$version")
            if (!keep.exists()) file.copyTo(keep)
            write(data)
        }
        return data
    }

    private fun broken(): StatsData {
        val aside = File(file.parentFile, "${file.name}.broken-${clock()}")
        if (!file.renameTo(aside)) file.copyTo(aside, overwrite = true)
        _problem.value = StatsProblem.Broken(aside.name)
        return StatsData()
    }

    /**
     * Applies [transform] to the current data and saves the result; returns it. Does nothing while the file is from a
     * newer version (returns the unchanged data).
     */
    suspend fun update(transform: (StatsData) -> StatsData): StatsData = lock.withLock {
        val current = loadLocked()
        if (_problem.value is StatsProblem.TooNew) return@withLock current
        val next = transform(current)
        if (next != current) {
            withContext(io) { write(next) }
            _data.value = next
        }
        next
    }

    /** True while the file is from a newer app version (nothing can be saved). */
    val readOnly: Boolean get() = _problem.value is StatsProblem.TooNew

    private fun write(data: StatsData) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(statsJson.encodeToString(JsonObject.serializer(), encodeStats(data)).toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("could not replace ${file.name}")
        }
    }
}
