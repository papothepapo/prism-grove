package com.prismml.grove.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.core.MessageRole
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.AccentPill
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen
import com.prismml.grove.ui.design.MarkdownText
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatScreen(
    app: PrismGroveApp,
    conversationId: Long?,
    onStartConversation: () -> Unit,
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
    val listState = rememberLazyListState()
    var composer by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    GradientScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPill(
                        text = if (conversationId == null || conversationId < 0) "Start a fresh chat" else "Bonsai chat ready",
                    )
                    Text("Chat like ChatGPT, fully on-device.", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = if (conversationId == null || conversationId < 0) {
                            "Create a conversation, download the 1-bit Bonsai 8B preset, and keep the whole loop on your phone."
                        } else {
                            "The composer stays anchored, message actions are one tap away, and the 1-bit Bonsai models keep storage under control."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPill("8B default", tint = MaterialTheme.colorScheme.secondary)
                        AccentPill("~1.15 GB", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    if (conversationId == null || conversationId < 0) {
                        FilledTonalButton(onClick = onStartConversation) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("New chat", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (conversationId == null || conversationId < 0) {
                EmptyChatState(modifier = Modifier.weight(1f), onStartConversation = onStartConversation)
            } else {
                if (state.messages.isEmpty()) {
                    EmptyConversationState(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                }

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 14.dp,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = composer,
                            onValueChange = { composer = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 8,
                            placeholder = {
                                Text("Message Bonsai 8B 1-bit")
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccentPill(
                                    text = if (state.isGenerating) "Generating" else "Ready",
                                    tint = if (state.isGenerating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                )
                                AccentPill("Local runtime", tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { viewModel.clear(conversationId) }) {
                                    Text("Clear")
                                }
                                if (state.isGenerating) {
                                    TextButton(onClick = { viewModel.cancel() }) {
                                        Text("Stop")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.send(conversationId, composer)
                                        composer = ""
                                    },
                                    enabled = composer.isNotBlank() && !state.isGenerating,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
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
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
        )
    }
}

@Composable
private fun EmptyChatState(
    modifier: Modifier = Modifier,
    onStartConversation: () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 26.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Text("Nothing open yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Create a chat, then message Bonsai with the same clean flow people expect from ChatGPT.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onStartConversation) {
                    Text("Start new conversation")
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 24.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccentPill("Prompt starter", tint = MaterialTheme.colorScheme.secondary)
                    AccentPill("1-bit runtime", tint = MaterialTheme.colorScheme.tertiary)
                }
                Text("Ask for something concrete.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Try summarizing notes, drafting a reply, or turning a rough idea into a plan. The composer is ready below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
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
    val isUser = message.role == MessageRole.USER
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.84f else 0.96f)
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onLongClick = { expanded = true },
                ),
            shape = RoundedCornerShape(28.dp),
            color = backgroundColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AccentPill(
                            text = when (message.role) {
                                MessageRole.USER -> "You"
                                MessageRole.ASSISTANT -> "Bonsai"
                                MessageRole.SYSTEM -> "System"
                            },
                            tint = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        )
                        if (message.status == "streaming") {
                            AccentPill("Streaming", tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    )
                }
                if (message.content.isBlank() && message.status == "streaming") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text("Generating reply…", fontWeight = FontWeight.Medium)
                    }
                } else {
                    MarkdownText(message.content)
                }
                if (expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onCopy) { Text("Copy") }
                        if (message.role == MessageRole.USER) {
                            TextButton(onClick = onEdit) { Text("Edit") }
                        }
                        TextButton(onClick = onRegenerate) { Text("Retry") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }
}
