package de.luwify.mensamd.update

/**
 * Tells when the main UI comes to the foreground: once per visit. A rotation (no app stop in between) is the same visit,
 * and the card popup never counts: it runs on a card tap, which must not start anything on the network.
 */
class MainUiGate {
    private var visiting = false

    /** MainActivity started; true = a new visit. */
    fun started(): Boolean = !visiting.also { visiting = true }

    /** No activity of the app is visible any more. */
    fun appStopped() {
        visiting = false
    }
}
