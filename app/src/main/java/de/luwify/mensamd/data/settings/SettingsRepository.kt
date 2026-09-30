package de.luwify.mensamd.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun settingsRepository(context: Context) = SettingsRepository(context.applicationContext.settingsDataStore)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val defaultMensaKey = stringPreferencesKey("default_mensa_id")

    /** Null until the user picked one: that is what triggers the first-run picker. */
    val defaultMensaId: Flow<String?> = store.data.map { it[defaultMensaKey] }

    suspend fun setDefaultMensaId(id: String) {
        store.edit { it[defaultMensaKey] = id }
    }
}
