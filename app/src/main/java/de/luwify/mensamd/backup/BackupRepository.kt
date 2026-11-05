package de.luwify.mensamd.backup

import de.luwify.mensamd.data.settings.SettingsRepository
import de.luwify.mensamd.stats.StatsStore

/** Builds a backup from the app's data and puts one back. The file I/O (content URIs) stays with the caller. */
class BackupRepository(
    private val settings: SettingsRepository,
    private val stats: StatsStore,
    private val language: () -> String,
    private val setLanguage: (String) -> Unit,
    private val appVersion: String?,
) {
    /** Everything a backup holds, as it is right now (also the undo snapshot before an import). */
    suspend fun current(): Backup = Backup(settings.exportAll(), stats.load(), language(), appVersion = appVersion)

    /**
     * Replaces settings (favorites included), statistics and language with [backup]. Refused while the statistics file
     * is from a newer app version (it is never overwritten).
     */
    suspend fun restore(backup: Backup) {
        stats.load()
        check(!stats.readOnly) { "statistics are read-only" }
        stats.update { backup.stats }
        settings.importAll(backup.settings)
        backup.language?.let(setLanguage)
    }
}
