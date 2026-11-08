package de.luwify.mensamd.card

import de.luwify.mensamd.stats.CardIdentity

/** What the card screen shows. Holds the balance in memory only; it is dropped when the screen closes. */
sealed interface CardScanState {
    data object NoNfc : CardScanState
    data object NfcOff : CardScanState
    data object Waiting : CardScanState
    data object Reading : CardScanState

    /**
     * A read card. [fingerprint] is a hash of the card's UID made in memory (see `CardIdentity`; null if the tag had no
     * id or the statistics are off): the card statistics compare it with the registered card. [seq] numbers the scans, so a statistics result can
     * be matched to its scan.
     */
    data class Success(val balance: CardBalance, val fingerprint: String?, val seq: Long) : CardScanState
    data object Unsupported : CardScanState
    data object Error : CardScanState
}

/** The card's id as a fingerprint (the bytes aren't kept); null if the tag has none. */
fun cardFingerprint(uid: ByteArray?): String? = uid?.takeIf { it.isNotEmpty() }?.let(CardIdentity::fingerprint)

/** [uid] is asked for only after a successful read (null = the card id isn't wanted, see `StatsRecorder.wantsCardId`). */
fun CardReadResult.toState(seq: Long, uid: () -> ByteArray?): CardScanState = when (this) {
    is CardReadResult.Success -> CardScanState.Success(balance, cardFingerprint(uid()), seq)
    CardReadResult.Unsupported -> CardScanState.Unsupported
    CardReadResult.Failed -> CardScanState.Error
}
