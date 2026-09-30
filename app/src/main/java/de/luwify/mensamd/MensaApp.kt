package de.luwify.mensamd

import android.app.Application

class MensaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
