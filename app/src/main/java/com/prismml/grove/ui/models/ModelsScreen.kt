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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.core.DownloadState
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen

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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Models", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                OutlinedButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                    Text("Import GGUF")
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.models, key = { it.id }) { model ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(model.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(model.description, style = MaterialTheme.typography.bodyMedium)
                            Text("${model.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.labelMedium)
                            if (model.downloadedBytes > 0 && model.sizeBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { model.downloadedBytes.toFloat() / model.sizeBytes.toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(
                                when (model.state) {
                                    DownloadState.READY -> "Ready"
                                    DownloadState.IMPORTED -> "Imported"
                                    DownloadState.DOWNLOADING -> "Downloading"
                                    DownloadState.QUEUED -> "Queued"
                                    DownloadState.PAUSED -> "Paused"
                                    DownloadState.ERROR -> model.errorMessage ?: "Download error"
                                    DownloadState.REMOTE -> "Remote"
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (model.state) {
                                    DownloadState.REMOTE, DownloadState.PAUSED, DownloadState.ERROR -> {
                                        OutlinedButton(onClick = { viewModel.download(model.id) }) { Text("Download") }
                                    }
                                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                                        OutlinedButton(onClick = { viewModel.cancel(model.id) }) { Text("Cancel") }
                                    }
                                    DownloadState.READY, DownloadState.IMPORTED -> {
                                        OutlinedButton(onClick = { viewModel.selectDefault(model.id) }) { Text(if (state.defaultModelId == model.id) "Selected" else "Select") }
                                        OutlinedButton(onClick = { viewModel.delete(model.id) }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
