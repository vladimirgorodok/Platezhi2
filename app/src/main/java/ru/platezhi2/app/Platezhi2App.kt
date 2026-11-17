package ru.platezhi2.app

import android.app.Application
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.reminders.Reminders

class Platezhi2App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.ensureChannel(this)
        container.cloudCoordinator.start()
    }
}
