package com.prismml.grove.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.AccentPill
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    app: PrismGroveApp,
    onOpenConversation: (Long) -> Unit,
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = SimpleFactory {
            HistoryViewModel(app.container.conversationRepository, app.container.settingsRepository)
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    GradientScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccentPill("Conversation library")
                            Text("Jump back into any thread.", style = MaterialTheme.typography.headlineMedium)
                        }
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear all")
                        }
                    }
                    Text(
                        "Prism Grove now opens like a proper chat client: quick search, direct re-entry, and a prominent new-chat action.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccentPill("${state.conversations.size} chats", tint = MaterialTheme.colorScheme.secondary)
                        FilledTonalButton(onClick = { viewModel.createConversation(onOpenConversation) }) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("New chat", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearch,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search conversations") },
                    singleLine = true,
                )
            }

            if (state.conversations.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No saved chats yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Start one from here and it will show up with its preview and timestamp.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.conversations, key = { it.id }) { conversation ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenConversation(conversation.id) },
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        conversation.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(conversation.updatedAt)),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                conversation.preview?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onOpenConversation(conversation.id) }) { Text("Open") }
                                    TextButton(onClick = { renameTarget = conversation.id to conversation.title }) { Text("Rename") }
                                    TextButton(onClick = { viewModel.deleteConversation(conversation.id) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { (id, title) ->
        var value by remember(id) { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameConversation(id, value)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Title") })
            },
        )
    }
}
