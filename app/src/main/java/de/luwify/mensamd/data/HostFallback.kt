package de.luwify.mensamd.data

/**
 * Which host to try first. Normally the main one ([hosts] first). When a fallback worked after the main host failed,
 * it is tried first for [rememberMillis], so every refresh doesn't wait for the main host's timeout again; after that
 * the main host gets the next chance. Using the remembered fallback doesn't extend the time. In memory only, shared by
 * all Mensen. Pure Kotlin; thread-safe (refreshes of different Mensen run in parallel).
 */
class HostFallback(
    private val hosts: List<String>,
    private val clock: () -> Long,
    private val rememberMillis: Long = 60 * 60 * 1000L,
) {
    init {
        require(hosts.isNotEmpty()) { "no hosts" }
    }

    private var remembered: String? = null
    private var since = 0L

    /** The hosts in the order to try them now. */
    @Synchronized
    fun order(): List<String> {
        val host = current() ?: return hosts
        return listOf(host) + (hosts - host)
    }

    /** A page from [host] was usable. */
    @Synchronized
    fun succeeded(host: String) {
        when {
            host == hosts.first() -> remembered = null
            current() != host -> {
                remembered = host
                since = clock()
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
