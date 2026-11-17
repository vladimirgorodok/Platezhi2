package ru.platezhi2.app.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.platezhi2.app.data.cloud.CloudSync
import ru.platezhi2.app.data.cloud.CloudSyncCoordinator
import ru.platezhi2.app.data.db.AppDatabase
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.SettingsRepository
import ru.platezhi2.app.data.transfer.TransferManager
import ru.platezhi2.app.media.ImageStore

/** Простой ручной контейнер зависимостей (без DI-фреймворков). */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** Живёт столько же, сколько приложение: фоновая синхронизация с облаком. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: AppDatabase = AppDatabase.get(appContext)

    val paymentRepository: PaymentRepository = PaymentRepository(
        db = database,
        cardDao = database.cardDao(),
        paymentDao = database.paymentDao(),
    )

    val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    val imageStore: ImageStore = ImageStore(appContext)

    val transferManager: TransferManager = TransferManager(appContext, paymentRepository, imageStore)

    val cloudSync: CloudSync = CloudSync(
        settings = settingsRepository,
        transfer = transferManager,
        repo = paymentRepository,
        images = imageStore,
    )

    val cloudCoordinator: CloudSyncCoordinator = CloudSyncCoordinator(
        context = appContext,
        settings = settingsRepository,
        repo = paymentRepository,
        sync = cloudSync,
        scope = appScope,
    )
}
