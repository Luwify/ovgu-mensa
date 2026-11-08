package de.luwify.mensamd.card

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import de.luwify.mensamd.stats.CardIdentity
import java.util.concurrent.atomic.AtomicLong

/**
 * Reads the Mensa card through NFC reader mode, only between [start] and [stop] (i.e. while
 * MainActivity is resumed). Reader mode needs no manifest intent filters.
 *
 * Privacy: nothing is logged or sent. The card's UID is looked at only after a successful read and
 * only if [wantsCardId] says so (statistics on); it then only becomes a fingerprint in memory
 * (`CardIdentity.fingerprint`). [onRead] hands the result to the card statistics, which store
 * anything only when the user turned them on and registered this card. [state] is the only other
 * place the result lives, and [stop] clears it.
 */
class NfcCardScanner(private val adapter: NfcAdapter?, private val wantsCardId: () -> Boolean = { false }) {
    /** Called on the NFC thread after every successful read (the card statistics). */
    @Volatile var onRead: ((CardScanState.Success) -> Unit)? = null


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
        val state = isoDep.readCard().toState(scanSeq.incrementAndGet()) { if (wantsCardId()) tag.id else null }
        if (state is CardScanState.Success && active) onRead?.invoke(state)
        publish(state)
    }

    /** Ignores results that arrive after the screen was left. */
    private fun publish(state: CardScanState) {
        if (active) _state.value = state
    }
}

/**
 * Scan numbers for the whole process: the statistics result ([de.luwify.mensamd.stats.StatsRecorder.last]) outlives an
 * activity (and its scanner), so a recreated activity must not start again at 1 and match an old result.
 */
private val scanSeq = AtomicLong()
