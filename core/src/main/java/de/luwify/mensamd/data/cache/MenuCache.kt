package de.luwify.mensamd.data.cache

import de.luwify.mensamd.model.MenuPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** The parsed page (legend included), when we fetched it (epoch millis) and whether from a fallback host. */
@Serializable
data class CachedMenu(val fetchedAt: Long, val page: MenuPage, val schema: Int = SCHEMA, val fromFallback: Boolean = false) {
    companion object {
        /** Bump when the model changes incompatibly; older files are then ignored and refetched. */
        const val SCHEMA = 1
    }
}

interface MenuCache {
    /** Null when there is no usable cache (missing, unreadable, corrupt or from another schema). */
    suspend fun read(mensaId: String): CachedMenu?
    suspend fun write(mensaId: String, menu: CachedMenu)
}

/** One JSON file per Mensa in [dir]. Blocking file IO; callers pick the dispatcher. */
class FileMenuCache(private val dir: File) : MenuCache {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun file(id: String) = File(dir, "menu-$id.json")

    override suspend fun read(mensaId: String): CachedMenu? {
        val f = file(mensaId)
        if (!f.isFile) return null
        return try {
            json.decodeFromString<CachedMenu>(f.readText()).takeIf { it.schema == CachedMenu.SCHEMA }
        } catch (_: Exception) {
            null // corrupt or incompatible: behave like an empty cache, the next fetch overwrites it
        }
    }

    override suspend fun write(mensaId: String, menu: CachedMenu) {
        dir.mkdirs()
        val target = file(mensaId)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.encodeToString(menu))
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("could not replace ${target.name}")
        }
    }
}
