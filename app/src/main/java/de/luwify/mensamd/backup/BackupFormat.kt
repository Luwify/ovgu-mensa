package de.luwify.mensamd.backup

import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.stats.StatsTooNewException
import de.luwify.mensamd.stats.decodeStats
import de.luwify.mensamd.stats.encodeStats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * One stored setting, typed like the DataStore value it came from. The settings are backed up generically (every
 * key of the settings DataStore except [BackupFormat.EXCLUDED_KEYS]): favorites with their infos, filters, theme,
 * notification and card statistics settings are all settings keys.
 */
@Serializable
sealed interface SettingValue {
    @Serializable @SerialName("bool") data class Bool(val value: Boolean) : SettingValue
    @Serializable @SerialName("int") data class IntValue(val value: Int) : SettingValue
    @Serializable @SerialName("long") data class LongValue(val value: Long) : SettingValue
    @Serializable @SerialName("float") data class FloatValue(val value: Float) : SettingValue
    @Serializable @SerialName("double") data class DoubleValue(val value: Double) : SettingValue
    @Serializable @SerialName("string") data class Text(val value: String) : SettingValue
    @Serializable @SerialName("strings") data class Texts(val value: Set<String>) : SettingValue
}

/** What a backup holds. [language] is the in-app language key ("system", "de", "en"). */
data class Backup(
    val settings: Map<String, SettingValue>,
    val stats: StatsData,
    val language: String?,
    val exportedAt: String? = null,
    val appVersion: String? = null,
)

sealed interface BackupResult {
    data class Ok(val backup: Backup) : BackupResult
    data object NotABackup : BackupResult

    /** Made by a newer version of the app (the envelope or the statistics inside). */
    data object TooNew : BackupResult
    data class Broken(val reason: String) : BackupResult
}

/**
 * The backup file: one versioned JSON envelope (like Stundenplan MD's backup) with the settings, the in-app language
 * and the card statistics (card hash and salt, scans, events, meals, open questions, own extras and dishes). The
 * statistics carry their own schema version and go through the same `StatsMigrations` as the stored file. Pure.
 */
object BackupFormat {
    const val FORMAT = "mensamd-backup"

    /** 1: settings, language, statistics (2026-11). Raise it and add a step to [STEPS] when the envelope changes. */
    const val VERSION = 1

    /** Device state that is not the user's data: what was already announced, whether a permission was asked. */
    val EXCLUDED_KEYS = setOf("notified_favorites", "notify_permission_explained", "notify_permission_requested")

    private val STEPS: Map<Int, (JsonObject) -> JsonObject> = emptyMap()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }

    fun encode(backup: Backup, now: Instant = Instant.now()): String {
        val obj = JsonObject(
            buildMap<String, JsonElement> {
                put("format", JsonPrimitive(FORMAT))
                put("version", JsonPrimitive(VERSION))
                put("exportedAt", JsonPrimitive(now.toString()))
                backup.appVersion?.let { put("appVersion", JsonPrimitive(it)) }
                backup.language?.let { put("language", JsonPrimitive(it)) }
                put("settings", JsonObject(backup.settings.filterKeys { it !in EXCLUDED_KEYS }.toSortedMap().mapValues { (_, v) -> json.encodeToJsonElement(SettingValue.serializer(), v) }))
                put("stats", encodeStats(backup.stats))
            },
        )
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun decode(text: String): BackupResult = try {
        val obj = json.parseToJsonElement(text) as? JsonObject
        val version = obj?.get("version")?.jsonPrimitive?.intOrNull ?: 1
        when {
            obj == null || obj["format"]?.jsonPrimitive?.content != FORMAT -> BackupResult.NotABackup
            version > VERSION -> BackupResult.TooNew
            else -> {
                var o: JsonObject = obj
                for (v in version until VERSION) o = STEPS.getValue(v)(o)
                val settings = o["settings"]?.jsonObject.orEmpty().mapNotNull { (key, value) ->
                    // A value of a type this version doesn't know is skipped, never fatal.
                    runCatching { key to json.decodeFromJsonElement(SettingValue.serializer(), value) }.getOrNull()
                }.toMap().filterKeys { it !in EXCLUDED_KEYS }
                val stats = o["stats"]?.jsonObject?.let(::decodeStats) ?: StatsData()
                BackupResult.Ok(
                    Backup(
                        settings, stats,
                        language = o["language"]?.jsonPrimitive?.content,
                        exportedAt = o["exportedAt"]?.jsonPrimitive?.content,
                        appVersion = o["appVersion"]?.jsonPrimitive?.content,
                    ),
                )
            }
        }
    } catch (e: StatsTooNewException) {
        BackupResult.TooNew
    } catch (e: Exception) {
        BackupResult.Broken(e.message ?: e::class.simpleName ?: "error")
    }

    /** The file name offered when exporting: `mensamd-backup-2026-11-05.json`. */
    fun fileName(date: java.time.LocalDate): String = "mensamd-backup-$date.json"
}
