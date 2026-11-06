package de.luwify.mensamd.data

/** A fallback host that worked, and since when (epoch millis). */
data class RememberedHost(val host: String, val since: Long)

/** Where [HostFallback] keeps its fallback across process starts. Blocking; never throws. */
interface HostMemory {
    fun read(): RememberedHost?
    fun write(host: RememberedHost?)
}

/**
 * Which host to try first. Normally the main one ([hosts] first). When a fallback worked after the main host failed,
 * it is tried first for [rememberMillis], so every refresh doesn't wait for the main host's timeout again; after that
 * the main host gets the next chance. Using the remembered fallback doesn't extend the time. Shared by all Mensen and
 * kept in [memory], so a cold start during an outage doesn't wait for the main host either. Pure Kotlin; thread-safe
 * (refreshes of different Mensen run in parallel). [restore], [order] and [succeeded] may touch the disk once.
 */
class HostFallback(
    private val hosts: List<String>,
    private val clock: () -> Long,
    private val rememberMillis: Long = 60 * 60 * 1000L,
    private val memory: HostMemory? = null,
) {
    init {
        require(hosts.isNotEmpty()) { "no hosts" }
    }

    private var remembered: String? = null
    private var since = 0L
    private var restored = memory == null

    /** Reads what an earlier process remembered; only the first call does anything. */
    @Synchronized
    fun restore() {
        if (restored) return
        restored = true
        memory?.read()?.let {
            remembered = it.host
            since = it.since
        }
    }

    /** The hosts in the order to try them now. */
    @Synchronized
    fun order(): List<String> {
        restore()
        val host = current() ?: return hosts
        return listOf(host) + (hosts - host)
    }

    /** A page from [host] was usable. */
    @Synchronized
    fun succeeded(host: String) {
        restore()
        when {
            host == hosts.first() -> if (remembered != null) {
                remembered = null
                memory?.write(null)
            }
            current() != host -> {
                remembered = host
                since = clock()
                memory?.write(RememberedHost(host, since))
            }
        }
    }

    /** The remembered fallback while it is valid (a clock that moved backwards ends it too). */
    private fun current(): String? {
        val host = remembered ?: return null
        val age = clock() - since
        if (host !in hosts || age !in 0 until rememberMillis) {
            remembered = null
            return null
        }
        return host
    }
}
