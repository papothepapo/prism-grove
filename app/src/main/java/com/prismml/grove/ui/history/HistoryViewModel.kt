package com.prismml.grove.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prismml.grove.core.ConversationSummary
import com.prismml.grove.data.chat.ConversationRepository
import com.prismml.grove.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val searchQuery: String = "",
)

class HistoryViewModel(
    private val conversationRepository: ConversationRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val defaultModelId: Flow<String?> = settingsRepository.settings.map { it.defaultModelId }

    val state: StateFlow<HistoryUiState> = combine(
        conversationRepository.observeConversations(),
        searchQuery,
    ) { conversations, query ->
        HistoryUiState(
            conversations = if (query.isBlank()) conversations else conversations.filter {
                it.title.contains(query, ignoreCase = true) || (it.preview?.contains(query, ignoreCase = true) == true)
            },
            searchQuery = query,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun createConversation(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val conversationId = conversationRepository.createConversation(defaultModelId.stateIn(viewModelScope, SharingStarted.Eagerly, null).value)
            onReady(conversationId)
        }
    }

    fun renameConversation(conversationId: Long, title: String) {
        viewModelScope.launch { conversationRepository.renameConversation(conversationId, title) }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch { conversationRepository.deleteConversation(conversationId) }
    }

    fun clearAll() {
        viewModelScope.launch { conversationRepository.clearAll() }
    }
}
