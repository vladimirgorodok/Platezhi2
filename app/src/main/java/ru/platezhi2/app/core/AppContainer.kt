package ru.platezhi2.app.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.platezhi2.app.data.cloud.CloudSync
import ru.platezhi2.app.data.cloud.CloudSyncCoordinator
import ru.platezhi2.app.data.db.AppDatabase
import ru.platezhi2.app.data.archive.ArchiveFiles
import ru.platezhi2.app.data.archive.LocalArchive
import ru.platezhi2.app.data.repo.PaymentRepository
import ru.platezhi2.app.data.settings.SettingsRepository
import ru.platezhi2.app.data.transfer.TransferManager
import ru.platezhi2.app.media.ImageStore
import java.io.File

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

    val transferManager: TransferManager = TransferManager(paymentRepository, imageStore)

    /**
     * Архив данных на устройстве: копия платежей вместе со значками.
     * Живёт в приватной папке приложения, поэтому переживает очистку данных в приложении.
     */
    val localArchive: LocalArchive = LocalArchive(
        files = ArchiveFiles(File(appContext.filesDir, "archive")),
        transfer = transferManager,
        repo = paymentRepository,
        scope = appScope,
    )

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
        archive = localArchive,
        scope = appScope,
    )
}
