package com.prismml.grove.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.core.MessageRole
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen
import com.prismml.grove.ui.design.MarkdownText
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatScreen(
    app: PrismGroveApp,
    conversationId: Long?,
    onConversationOpened: (Long) -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-${conversationId ?: -1L}",
        factory = SimpleFactory {
            ChatViewModel(conversationId, app.container.conversationRepository, app.container.chatRepository)
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    var composer by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    GradientScreen {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Chat", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (conversationId == null || conversationId < 0) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No chat selected.")
                        Text("Create a chat from the History tab to start a clean conversation.")
                    }
                }
            } else {
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = { clipboard?.setPrimaryClip(ClipData.newPlainText("message", message.content)) },
                            onDelete = { viewModel.delete(conversationId, message.id) },
                            onEdit = { if (message.role == MessageRole.USER) editTarget = message.id to message.content },
                            onRegenerate = { viewModel.regenerate(conversationId, message.id) },
                        )
                    }
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Prompt") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                viewModel.send(conversationId, composer)
                                composer = ""
                            }) { Text("Send") }
                            TextButton(onClick = { viewModel.cancel() }, enabled = state.isGenerating) { Text("Cancel") }
                            TextButton(onClick = { viewModel.clear(conversationId) }) { Text("Clear chat") }
                        }
                    }
                }
            }
        }
    }

    editTarget?.let { (messageId, currentContent) ->
        var value by remember(messageId) { mutableStateOf(currentContent) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    if (conversationId != null && conversationId >= 0) {
                        viewModel.edit(conversationId, messageId, value)
                    }
                    editTarget = null
                }) { Text("Resend") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } },
            title = { Text("Edit message") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth()) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: com.prismml.grove.core.ChatMessage,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { expanded = true },
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            MarkdownText(message.content.ifBlank { if (message.status == "streaming") "Generating..." else "" })
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopy) { Text("Copy") }
                    if (message.role == MessageRole.USER) {
                        TextButton(onClick = onEdit) { Text("Edit") }
                    }
                    TextButton(onClick = onRegenerate) { Text("Resend") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}
