package de.luwify.mensamd.card

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the Mensa card through NFC reader mode, only between [start] and [stop] (i.e. while the
 * card screen is resumed). Reader mode needs no manifest intent filters, and the card never opens
 * the app from outside.
 *
 * Privacy: nothing is logged, stored or sent; the card UID is never read. [state] is the only
 * place the result lives, and [stop] clears it.
 */
class NfcCardScanner(private val adapter: NfcAdapter?) {
    /** False on devices without NFC hardware: the card icon is hidden then. */
    val available: Boolean get() = adapter != null

    private val _state = MutableStateFlow<CardScanState>(if (adapter == null) CardScanState.NoNfc else CardScanState.Waiting)
    val state: StateFlow<CardScanState> = _state.asStateFlow()

    @Volatile private var active = false

    fun start(activity: Activity) {
        val adapter = adapter
        when {
            adapter == null -> _state.value = CardScanState.NoNfc
            !adapter.isEnabled -> _state.value = CardScanState.NfcOff
            else -> {
                active = true
                _state.value = CardScanState.Waiting
                try {
                    adapter.enableReaderMode(
                        activity,
                        ::onTag,
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        null,
                    )
                } catch (e: RuntimeException) {
                    active = false
                    _state.value = CardScanState.Error
                }
            }
        }
    }

    fun stop(activity: Activity) {
        active = false
        try {
            adapter?.disableReaderMode(activity)
        } catch (e: RuntimeException) {
            // activity already finishing: reader mode ends with it
        }
        _state.value = if (adapter == null) CardScanState.NoNfc else CardScanState.Waiting
    }

    /** Back to "waiting" (the card screen was closed): a finished result must not linger or reopen the screen. */
    fun reset() {
        if (active) _state.value = CardScanState.Waiting
    }

    /** Runs on an NFC binder thread; blocking I/O is fine here. */
    private fun onTag(tag: Tag) {
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) { // e.g. MIFARE Classic: not supported
            publish(CardScanState.Unsupported)
            return
        }
        publish(CardScanState.Reading)
        publish(isoDep.readCard().toState())
    }

    /** Ignores results that arrive after the screen was left. */
    private fun publish(state: CardScanState) {
        if (active) _state.value = state
    }
}
