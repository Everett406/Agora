package com.newoether.agora.viewmodel

import android.app.Application
import android.content.Context
import com.newoether.agora.R
import com.newoether.agora.api.local.LocalProvider
import com.newoether.agora.automation.ConversationExecutionCoordinator
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.SettingsRepository
import com.newoether.agora.util.SnackbarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Process-scoped chat runtime shared by every client of this process. It lives for the whole
 * process on the app scope, so chat work it owns keeps running when no Activity or ViewModel
 * exists.
 *
 * It owns the foreground generation core and the message commands (send, regenerate, edit,
 * delete, compact, stop) plus the conversation commands (rename, delete, fork, share). Commands
 * name their conversation and origin client explicitly. Per-client state (open conversation,
 * scroll, composer drafts, new-chat workspace) stays with each client.
 */
class ChatRuntime(
    application: Application,
    appContext: Context,
    conversations: ConversationRepository,
    settings: SettingsRepository,
    registry: ConversationStateRegistry,
    providerRegistry: ProviderRegistry,
    localProvider: LocalProvider,
    executionCoordinator: ConversationExecutionCoordinator,
    scope: CoroutineScope,
) {
    // replay=0: events raised while no client is collecting are dropped rather than replayed
    // stale to the next client. The 1-slot buffer keeps tryEmit lossless for slow collectors.
    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Clients currently attached to this runtime (see [ChatClient]). */
    internal val clients = ChatClients()

    /** Runtime-wide messages every connected client may show. */
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents.asSharedFlow()

    internal val generationManager: GenerationManager by lazy {
        GenerationManager(
            app = application,
            conversations = conversations,
            context = appContext,
            customProviders = { settings.customProviders.value },
        )
    }

    /** Stateless request assembly shared by every command; context previews read it too. */
    internal val requestBuilder = GenerationRequestBuilder(
        settings = settings,
        convRepo = conversations,
        providerRegistry = providerRegistry,
        appContext = appContext,
    )

    internal val messageGeneration: MessageGenerationController by lazy {
        MessageGenerationController(
            scope = scope,
            application = application,
            appContext = appContext,
            convRepo = conversations,
            settings = settings,
            registry = registry,
            generationManagerProvider = { generationManager },
            requestBuilder = requestBuilder,
            payloadBuilder = MessagePayloadBuilder(),
            providerRegistry = providerRegistry,
            localProvider = localProvider,
            executionCoordinator = executionCoordinator,
            clients = clients,
            onSnackbar = { message -> _snackbarEvents.tryEmit(SnackbarEvent(message)) },
        )
    }

    internal val generationStop: GenerationStopAdapter by lazy {
        GenerationStopAdapter(
            registry = registry,
            clients = clients,
            finalizer = GenerationFinalizer(conversations),
            failureText = { appContext.getString(R.string.failed_to_generate) },
        )
    }

    internal val conversationLifecycle = ConversationLifecycleController(
        conversations = conversations,
        scope = scope,
        clients = clients,
        // Automation loops are gone; deleting a conversation has nothing to stop besides its Run.
        stopLoop = {},
        tryWithConversationLock = { conversationId, block ->
            executionCoordinator.tryWithConversationLock(conversationId) { block() }
        },
        removeRuntime = registry::remove,
        stopGeneration = { conversationId, origin -> generationStop.stop(conversationId, origin) },
        deletedElsewhereText = { appContext.getString(R.string.conversation_deleted_elsewhere) },
    )

    internal val conversationForkShare = ConversationForkShareController(
        service = ConversationForkShareService(
            conversations,
            settings,
            File(application.filesDir, "fork-attachments"),
        ),
        scope = scope,
        forkFailureText = { reason -> appContext.getString(R.string.conversation_fork_failed, reason) },
        shareFailureText = { reason -> appContext.getString(R.string.conversation_share_failed, reason) },
    )

    init {
        // The runtime is the registry's single, process-lifetime callback owner.
        registry.attachUiCallbacks(this) { state ->
            state.onActive = { conversationId ->
                // Published synchronously with the slot claim so Stop and edit closure are immediate.
                clients.generationActivityChanged(conversationId, active = true)
            }
            state.onIdle = { conversationId ->
                clients.generationActivityChanged(conversationId, active = false)
            }
            state.onStreamCommit = clients::commitTerminalStreamingMessage
            state.onQueueDrainRequested = { settledState ->
                settledState.scope.launch {
                    messageGeneration.drainQueuedAfterGeneration(settledState)
                }
            }
        }
    }
}
