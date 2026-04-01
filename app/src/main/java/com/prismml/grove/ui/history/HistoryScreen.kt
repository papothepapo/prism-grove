package com.prismml.grove.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.SimpleFactory
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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("History", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { viewModel.clearAll() }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear all", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search conversations") },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.conversations, key = { it.id }) { conversation ->
                    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onOpenConversation(conversation.id) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(conversation.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(conversation.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            conversation.preview?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { renameTarget = conversation.id to conversation.title }) { Text("Rename") }
                                TextButton(onClick = { viewModel.deleteConversation(conversation.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { viewModel.createConversation(onOpenConversation) }) {
                Icon(Icons.Rounded.Add, contentDescription = "New chat")
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
