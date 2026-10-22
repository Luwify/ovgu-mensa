package de.luwify.mensamd

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration

class MensaApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Update check whenever the app comes to the foreground (throttled, if on). Not per activity: rotations
        // and switching between activities don't count.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.updates.onForeground()
        })
    }

    /** On-demand WorkManager initialization (the startup initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
