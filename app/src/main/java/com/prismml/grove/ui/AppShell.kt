package com.prismml.grove.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.chat.ChatScreen
import com.prismml.grove.ui.history.HistoryScreen
import com.prismml.grove.ui.models.ModelsScreen
import com.prismml.grove.ui.navigation.Destination
import com.prismml.grove.ui.settings.SettingsScreen

@Composable
fun AppShell(
    app: PrismGroveApp,
    lastOpenedConversationId: Long?,
    onConversationOpened: (Long) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Destination.History,
                    Destination.Chat(lastOpenedConversationId),
                    Destination.Models,
                    Destination.Settings,
                ).forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute?.hierarchy?.any { it.route == destination.routePattern } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    is Destination.History -> Icons.Rounded.History
                                    is Destination.Chat -> Icons.Rounded.Chat
                                    is Destination.Models -> Icons.Rounded.Folder
                                    is Destination.Settings -> Icons.Rounded.Settings
                                },
                                contentDescription = destination.label,
                            )
                        },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.History.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.History.routePattern) {
                HistoryScreen(
                    app = app,
                    onOpenConversation = { conversationId ->
                        onConversationOpened(conversationId)
                        navController.navigate(Destination.Chat(conversationId).route)
                    },
                )
            }
            composable(Destination.Chat(null).routePattern) { backStack ->
                val conversationId = backStack.arguments?.getString("conversationId")?.toLongOrNull()
                ChatScreen(
                    app = app,
                    conversationId = conversationId,
                    onConversationOpened = onConversationOpened,
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
