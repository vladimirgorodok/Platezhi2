package ru.platezhi2.app.core

import android.content.Context
import ru.platezhi2.app.data.db.AppDatabase
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.SettingsRepository
import ru.platezhi2.app.data.transfer.TransferManager
import ru.platezhi2.app.logos.LogoRepository

/** Простой ручной контейнер зависимостей (без DI-фреймворков). */
class AppContainer(context: Context) {

    val appContext: android.content.Context = context.applicationContext

    private val database: AppDatabase = AppDatabase.get(appContext)

    val paymentRepository: PaymentRepository = PaymentRepository(database.paymentDao())

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val logoRepository: LogoRepository = LogoRepository(appContext)

    val transferManager: TransferManager = TransferManager(appContext, paymentRepository)
}
