package com.prismml.grove.ui.models

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.core.DownloadState
import com.prismml.grove.core.ModelItem
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.AccentPill
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen
import kotlin.math.roundToInt

@Composable
fun ModelsScreen(app: PrismGroveApp) {
    val viewModel: ModelsViewModel = viewModel(
        factory = SimpleFactory { ModelsViewModel(app.container.modelRepository, app.container.settingsRepository) }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccentPill("Model library")
                            Text("Use the 1-bit Bonsai lineup.", style = MaterialTheme.typography.headlineMedium)
                        }
                        FilledTonalButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                            androidx.compose.material3.Icon(Icons.Rounded.UploadFile, contentDescription = null)
                            Text("Import", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Text(
                        "The default path now points to PrismML's small GGUF releases. Bonsai 8B 1-bit is the recommended preset and should stay close to a 1.15 GB download.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPill("8B recommended", tint = MaterialTheme.colorScheme.secondary)
                        AccentPill("1.15 GB", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.models, key = { it.id }) { model ->
                    ModelCard(
                        model = model,
                        isDefault = state.defaultModelId == model.id,
                        onDownload = { viewModel.download(model.id) },
                        onCancel = { viewModel.cancel(model.id) },
                        onSelect = { viewModel.selectDefault(model.id) },
                        onDelete = { viewModel.delete(model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelItem,
    isDefault: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        model.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
                if (isDefault) {
                    AccentPill("Default", tint = MaterialTheme.colorScheme.secondary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentPill(formatSize(model.sizeBytes), tint = MaterialTheme.colorScheme.primary)
                model.contextLength?.let { AccentPill("${it / 1024}K ctx", tint = MaterialTheme.colorScheme.tertiary) }
                model.architecture?.let { AccentPill(it.uppercase(), tint = MaterialTheme.colorScheme.secondary) }
            }

            if (model.downloadedBytes > 0 && model.sizeBytes > 0 && model.state != DownloadState.REMOTE) {
                LinearProgressIndicator(
                    progress = { model.downloadedBytes.toFloat() / model.sizeBytes.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statusTint = when (model.state) {
                    DownloadState.READY, DownloadState.IMPORTED -> MaterialTheme.colorScheme.secondary
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> MaterialTheme.colorScheme.primary
                    DownloadState.ERROR -> MaterialTheme.colorScheme.error
                    DownloadState.PAUSED -> MaterialTheme.colorScheme.tertiary
                    DownloadState.REMOTE -> MaterialTheme.colorScheme.outline
                }
                AccentPill(
                    text = when (model.state) {
                        DownloadState.READY -> "Ready"
                        DownloadState.IMPORTED -> "Imported"
                        DownloadState.DOWNLOADING -> "Downloading ${downloadProgress(model)}"
                        DownloadState.QUEUED -> "Queued"
                        DownloadState.PAUSED -> "Paused"
                        DownloadState.ERROR -> "Error"
                        DownloadState.REMOTE -> "Remote"
                    },
                    tint = statusTint,
                )
                if (model.state == DownloadState.ERROR && !model.errorMessage.isNullOrBlank()) {
                    Text(
                        model.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (model.state) {
                    DownloadState.REMOTE, DownloadState.PAUSED, DownloadState.ERROR -> {
                        FilledTonalButton(onClick = onDownload) {
                            androidx.compose.material3.Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
                            Text("Download", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                    DownloadState.READY, DownloadState.IMPORTED -> {
                        FilledTonalButton(onClick = onSelect) {
                            androidx.compose.material3.Icon(Icons.Rounded.Done, contentDescription = null)
                            Text(if (isDefault) "Selected" else "Use model", modifier = Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }

            if (model.id == "bonsai-8b") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        "This is the main mobile-quality preset. The 1-bit GGUF should stay near a 1.15 GB on-disk download.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

private fun downloadProgress(model: ModelItem): String {
    if (model.sizeBytes <= 0) return ""
    val percent = ((model.downloadedBytes.toDouble() / model.sizeBytes.toDouble()) * 100).roundToInt()
    return "$percent%"
}

private fun formatSize(sizeBytes: Long): String {
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (sizeBytes >= gib) {
        "%.2f GiB".format(sizeBytes / gib)
    } else {
        "%.0f MB".format(sizeBytes / mib)
    }
}
