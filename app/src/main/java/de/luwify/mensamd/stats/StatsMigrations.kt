package de.luwify.mensamd.stats

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The stored file (or a backup) was written by a newer app version: it is never overwritten or downgraded. */
class StatsTooNewException(val version: Int) : Exception("statistics version $version is newer than ${StatsMigrations.CURRENT}")

/**
 * Schema versions of [StatsData]. Any change of the stored shape: raise [CURRENT], add the step `STEPS[old]`
 * (old -> old + 1, on the raw JSON) and a test in StatsMigrationsTest. Never drop or rename a stored field without a
 * step. Version 1 = the first release of the statistics (2026-11).
 */
object StatsMigrations {
    const val CURRENT = 1

    /** n -> n + 1. */
    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    fun versionOf(obj: JsonObject): Int = obj["version"]?.jsonPrimitive?.intOrNull ?: 1

    /** Brings [obj] to [CURRENT] step by step; throws [StatsTooNewException] for a newer file. */
    fun migrate(obj: JsonObject, steps: Map<Int, (JsonObject) -> JsonObject> = STEPS, current: Int = CURRENT): JsonObject {
        var version = versionOf(obj)
        if (version > current) throw StatsTooNewException(version)
        var out = obj
        while (version < current) {
            out = steps.getValue(version)(out)
            version++
        }
        return JsonObject(out + ("version" to JsonPrimitive(current)))
    }
}
