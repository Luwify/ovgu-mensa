package de.luwify.mensamd

import android.app.Application
import androidx.work.Configuration

class MensaApp : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(this) }

    /** On-demand WorkManager initialization (the startup initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
