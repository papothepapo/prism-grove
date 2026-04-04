package com.prismml.grove.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.chat.ChatScreen
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.history.HistoryScreen
import com.prismml.grove.ui.models.ModelsScreen
import com.prismml.grove.ui.navigation.Destination
import com.prismml.grove.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AppShell(
    app: PrismGroveApp,
    lastOpenedConversationId: Long?,
    onConversationOpened: (Long) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val scope = rememberCoroutineScope()
    val navItems = listOf(
        Destination.History,
        Destination.Chat(lastOpenedConversationId),
        Destination.Models,
        Destination.Settings,
    )

    fun openChat(conversationId: Long) {
        onConversationOpened(conversationId)
        navController.navigate(Destination.Chat(conversationId).route) {
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        navItems.forEach { destination ->
                            val selected = currentRoute?.hierarchy?.any { it.route == destination.routePattern } == true
                            TextButton(
                                onClick = {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = when (destination) {
                                            is Destination.History -> Icons.Rounded.History
                                            is Destination.Chat -> Icons.AutoMirrored.Rounded.Chat
                                            is Destination.Models -> Icons.Rounded.Folder
                                            is Destination.Settings -> Icons.Rounded.Settings
                                        },
                                        contentDescription = destination.label,
                                        tint = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        },
                                    )
                                    Text(
                                        text = destination.label,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = lastOpenedConversationId?.let { Destination.Chat(it).route } ?: Destination.History.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.History.routePattern) {
                HistoryScreen(
                    app = app,
                    onOpenConversation = { conversationId ->
                        openChat(conversationId)
                    },
                )
            }
            composable(Destination.Chat(null).routePattern) { backStack ->
                val conversationId = backStack.arguments?.getString("conversationId")?.toLongOrNull()
                ChatScreen(
                    app = app,
                    conversationId = conversationId,
                    onStartConversation = {
                        scope.launch {
                            val defaultModelId = app.container.settingsRepository.settings.first().defaultModelId
                            val newConversationId = app.container.conversationRepository.createConversation(defaultModelId)
                            openChat(newConversationId)
                        }
                    },
                )
            }
            composable(Destination.Models.routePattern) {
                ModelsScreen(app = app)
            }
            composable(Destination.Settings.routePattern) {
                SettingsScreen(app = app)
            }
        }
    }
}
