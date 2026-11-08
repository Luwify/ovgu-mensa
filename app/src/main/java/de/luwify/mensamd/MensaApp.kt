package de.luwify.mensamd

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import de.luwify.mensamd.update.MainUiGate
import kotlinx.coroutines.launch

class MensaApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }
    private val mainUi = MainUiGate()

    override fun onCreate() {
        super.onCreate()
        // Ends a visit to the main UI (see MainUiGate). Comes with a short delay, so not on rotations.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = mainUi.appStopped()
        })
    }

    /** MainActivity started: update check (throttled, if on), payments left to match. Not from the card popup: a card tap starts no network. */
    fun mainUiStarted() {
        if (!mainUi.started()) return
        container.updates.onForeground()
        container.appScope.launch { container.statsRecorder.matchPending() }
    }

    /** On-demand WorkManager initialization (the startup initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
