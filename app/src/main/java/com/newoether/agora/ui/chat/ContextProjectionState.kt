package com.newoether.agora.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.newoether.agora.viewmodel.ChatViewModel

/** Every value capable of changing fixed system-prompt or tool-definition token cost. */
@Composable
internal fun rememberContextProjectionInvalidationKey(
    viewModel: ChatViewModel,
    requestConfiguration: List<Any?>,
): List<Any?> {
    val settings = viewModel.settings
    val activePromptId by settings.activeSystemPromptId.collectAsState()
    val pendingPromptId by viewModel.pendingSystemPromptId.collectAsState()
    val systemPrompts by settings.systemPrompts.collectAsState()
    val accessSavedMemories by settings.accessSavedMemories.collectAsState()
    val accessActiveMemory by settings.accessActiveMemory.collectAsState()
    val accessPastConversations by settings.accessPastConversations.collectAsState()
    return requestConfiguration + listOf(
        activePromptId,
        pendingPromptId,
        systemPrompts,
        accessSavedMemories,
        accessActiveMemory,


        accessPastConversations,








    )
}
