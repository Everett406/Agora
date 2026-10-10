package com.newoether.agora.di

import android.app.Application
import android.content.Context
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.data.local.ChatDao
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.ConversationSettingsTransferCoordinator
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.data.AutoBackupManager
import com.newoether.agora.api.LocalModelRuntime
import com.newoether.agora.api.local.LocalProvider
import com.newoether.agora.automation.ConversationExecutionCoordinator
import com.newoether.agora.service.MaintenanceDebtWorker
import com.newoether.agora.viewmodel.ChatRuntime
import com.newoether.agora.viewmodel.ChatViewModelFactory
import com.newoether.agora.viewmodel.ConversationStateRegistry
import com.newoether.agora.viewmodel.ProviderRegistry
import kotlinx.coroutines.launch

/**
 * Centralized dependency container (manual DI).
 *
 * Replaces the ad-hoc dependency creation previously spread across MainActivity.
 * The validated database is injected by AgoraApplication's startup gate; all shared
 * dependencies are then created once and reused.
 *
 * This is a stepping stone toward a full DI framework (Hilt/Koin);
 * for a single-module project it provides sufficient decoupling and
 * testability without annotation processing overhead.
 */
class AppContainer(
    private val appContext: Context,
    val database: ChatDatabase,
) {
    private val application = appContext.applicationContext as Application

    private fun currentAppVersion(): String =
        try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    init {
        LocalModelRuntime.initialize(application.applicationInfo.nativeLibraryDir)
    }

    /** App-lifetime scope that backs the shared settings StateFlows.
     *  The handler is the last line of defense: children launched directly on this scope
     *  (settings sync) have no other parent to report to, and an
     *  uncaught exception here would otherwise kill the whole process. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                com.newoether.agora.util.DebugLog.e("AppContainer", "Uncaught in appScope", e)
            }
    )

    // ── Data Layer ────────────────────────────────────────────

    val settingsManager: SettingsManager by lazy { SettingsManager(appContext) }
    val chatDao: ChatDao by lazy { database.chatDao() }

    // ── Repositories ──────────────────────────────────────────

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(chatDao = chatDao, database = database)
    }

    @Volatile
    private var processServicesStarted = false

    /** Starts necessary process work after the narrow conversation list has published. */
    @Synchronized
    fun startProcessServices() {
        if (processServicesStarted) return
        providerRegistry.ensureStarted()
        processServicesStarted = true
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (database.maintenanceDebtDao().hasDebt()) MaintenanceDebtWorker.schedule()
            } catch (error: Exception) {
                com.newoether.agora.util.DebugLog.e(
                    "AppContainer",
                    "Failed to schedule maintenance debt",
                    error,
                )
            }
        }
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            conversationSettingsTransfers.replayPending()
        }
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            settingsManager,
            appScope,
            touchConversationData = { conversationId ->
                conversationRepository.touchConversationData(conversationId)
            },
        ).also {
            LocalModelRuntime.bindIdleRetention(it.localModelIdleRetentionMinutes, appScope)
        }
    }
    val conversationSettingsTransfers: ConversationSettingsTransferCoordinator by lazy {
        ConversationSettingsTransferCoordinator(conversationRepository, settingsRepository)
    }

    // ── Generation singletons (process-scoped) ────────────────
    // [localProvider] must be unique per process; LocalModelRuntime owns the one embedded model
    // lifecycle. [providerRegistry] holds the live provider map the
    // generation pipeline reads and runs the long-lived credential/model sync jobs.

    val localProvider: LocalProvider by lazy { LocalProvider(appContext, settingsRepository) }

    val providerRegistry: ProviderRegistry by lazy {
        ProviderRegistry(
            settingsRepository,
            conversationRepository,
            localProvider,
            appScope,
            currentAppVersion(),
        )
    }

    /** Serializes every generation touching the same conversation. */
    val conversationExecutionCoordinator: ConversationExecutionCoordinator by lazy {
        ConversationExecutionCoordinator()
    }

    /** Foreground generation slots survive Activity/ViewModel recreation within this process. */
    val conversationStateRegistry: ConversationStateRegistry by lazy {
        ConversationStateRegistry { attachments ->
            appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                conversationRepository.deleteUnreferencedDraftAttachmentFiles(attachments)
            }
        }
    }

    // ── Auto Backup ───────────────────────────────────────────

    val autoBackupManager: AutoBackupManager by lazy {
        AutoBackupManager(appContext, settingsManager)
    }

    // Process-scoped chat runtime shared by every client.
    val chatRuntime: ChatRuntime by lazy {
        ChatRuntime(
            application = application,
            appContext = appContext,
            conversations = conversationRepository,
            settings = settingsRepository,
            registry = conversationStateRegistry,
            providerRegistry = providerRegistry,
            localProvider = localProvider,
            executionCoordinator = conversationExecutionCoordinator,
            scope = appScope,
        )
    }

    // ── ViewModel Factory ─────────────────────────────────────

    fun chatViewModelFactory(): ChatViewModelFactory =
        ChatViewModelFactory(
            application, database, chatDao, settingsManager, appContext,
            autoBackupManager, conversationRepository, settingsRepository, conversationSettingsTransfers,
            ::startProcessServices, providerRegistry,
            conversationExecutionCoordinator, conversationStateRegistry,
            chatRuntime,
        )
}
