package com.railbite.picker

import android.app.Application
import com.railbite.core.api.RailBiteClient
import com.railbite.core.data.RailBiteSettings

class RailBitePickerApp : Application() {

    lateinit var container: PickerContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PickerContainer(this)
    }
}

/** Tiny manual service locator (no DI framework on purpose). */
class PickerContainer(val app: Application) {
    val settings = RailBiteSettings(app)
    val client = RailBiteClient(settings)
}
