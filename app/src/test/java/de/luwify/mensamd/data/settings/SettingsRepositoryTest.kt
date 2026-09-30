package de.luwify.mensamd.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun defaultMensa_isNullUntilSet_thenPersists() = runBlocking {
        val file = File(tmp.root, "settings.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val repo = SettingsRepository(store)
            assertNull(repo.defaultMensaId.first())
            repo.setDefaultMensaId("herrenkrug")
            assertEquals("herrenkrug", repo.defaultMensaId.first())
        } finally {
            scope.cancel()
        }
        // A fresh store on the same file sees the value (it really hit the disk).
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo2 = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file })
            assertEquals("herrenkrug", repo2.defaultMensaId.first())
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun mensaOrder_isEmptyUntilSet_thenPersistsInOrder() = runBlocking {
        val file = File(tmp.root, "order.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            assertEquals(emptyList<String>(), repo.mensaOrder.first())
            repo.setMensaOrder(listOf("stendal", "herrenkrug", "kellercafe"))
            assertEquals(listOf("stendal", "herrenkrug", "kellercafe"), repo.mensaOrder.first())
        } finally {
            scope.cancel()
        }
    }
}
