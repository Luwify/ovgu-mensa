package de.luwify.mensamd.card

/** What the card screen shows. Holds the balance in memory only; it is dropped when the screen closes. */
sealed interface CardScanState {
    data object NoNfc : CardScanState
    data object NfcOff : CardScanState
    data object Waiting : CardScanState
    data object Reading : CardScanState
    data class Success(val balance: CardBalance) : CardScanState
    data object Unsupported : CardScanState
    data object Error : CardScanState
}

fun CardReadResult.toState(): CardScanState = when (this) {
    is CardReadResult.Success -> CardScanState.Success(balance)
    CardReadResult.Unsupported -> CardScanState.Unsupported
    CardReadResult.Failed -> CardScanState.Error
}
