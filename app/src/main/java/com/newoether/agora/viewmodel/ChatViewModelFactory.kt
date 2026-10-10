package com.newoether.agora.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.newoether.agora.data.AutoBackupManager
import com.newoether.agora.data.SettingsManager
import com.newoether.agora.automation.ConversationExecutionCoordinator
import com.newoether.agora.data.local.ChatDao
import com.newoether.agora.data.local.ChatDatabase
import com.newoether.agora.data.repository.ConversationRepository
import com.newoether.agora.data.repository.ConversationSettingsTransferCoordinator
import com.newoether.agora.data.repository.SettingsRepository

class ChatViewModelFactory(
    private val application: Application,
    private val database: ChatDatabase,
    private val chatDao: ChatDao,
    private val settingsManager: SettingsManager,
    private val context: Context,
    private val autoBackupManager: AutoBackupManager,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val conversationSettingsTransfers: ConversationSettingsTransferCoordinator,
    private val startProcessServices: () -> Unit,
    private val providerRegistry: ProviderRegistry,
    private val conversationExecutionCoordinator: ConversationExecutionCoordinator,
    private val conversationStateRegistry: ConversationStateRegistry,
    private val chatRuntime: ChatRuntime,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                application, database, chatDao, settingsManager, context,
                autoBackupManager, conversationRepository, settingsRepository,
                conversationSettingsTransfers, startProcessServices, providerRegistry,
                conversationExecutionCoordinator, conversationStateRegistry,
                chatRuntime,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
