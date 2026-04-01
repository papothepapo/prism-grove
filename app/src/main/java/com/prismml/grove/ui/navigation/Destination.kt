package com.prismml.grove.ui.navigation

sealed class Destination(
    val routePattern: String,
    val route: String,
    val label: String,
) {
    data object History : Destination("history", "history", "History")
    data class Chat(private val conversationId: Long?) :
        Destination("chat/{conversationId}", "chat/${conversationId ?: -1L}", "Chat")
    data object Models : Destination("models", "models", "Models")
    data object Settings : Destination("settings", "settings", "Settings")
}
