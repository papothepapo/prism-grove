package com.prismml.grove.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prismml.grove.core.ChatMessage
import com.prismml.grove.data.chat.ChatRepository
import com.prismml.grove.data.chat.ConversationRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

class ChatViewModel(
    conversationId: Long?,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {
    private val activeConversationId = MutableStateFlow(conversationId)
    private val isGenerating = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<ChatUiState> = combine(
        activeConversationId.flatMapLatest { id ->
            if (id == null || id < 0) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                conversationRepository.observeMessages(id)
            }
        },
        isGenerating,
        errorMessage,
    ) { messages, generating, error ->
        ChatUiState(messages = messages, isGenerating = generating, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send(conversationId: Long, prompt: String) {
        if (prompt.isBlank()) return
        isGenerating.value = true
        errorMessage.value = null
        viewModelScope.launch {
            chatRepository.sendMessage(conversationId, prompt)
                .onFailure { errorMessage.value = it.message }
            isGenerating.value = false
        }
    }

    fun regenerate(conversationId: Long, anchorMessageId: Long) {
        isGenerating.value = true
        errorMessage.value = null
        viewModelScope.launch {
            chatRepository.regenerateFrom(conversationId, anchorMessageId)
                .onFailure { errorMessage.value = it.message }
            isGenerating.value = false
        }
    }

    fun edit(conversationId: Long, messageId: Long, content: String) {
        isGenerating.value = true
        errorMessage.value = null
        viewModelScope.launch {
            chatRepository.editMessage(conversationId, messageId, content)
                .onFailure { errorMessage.value = it.message }
            isGenerating.value = false
        }
    }

    fun delete(conversationId: Long, messageId: Long) {
        viewModelScope.launch { chatRepository.deleteMessage(conversationId, messageId) }
    }

    fun clear(conversationId: Long) {
        viewModelScope.launch { conversationRepository.clearConversation(conversationId) }
    }

    fun cancel() {
        chatRepository.cancelGeneration()
        isGenerating.value = false
    }
}
