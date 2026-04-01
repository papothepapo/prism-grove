package com.prismml.grove.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.prismml.grove.core.AppSettings
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen

@Composable
fun SettingsScreen(app: PrismGroveApp) {
    val viewModel: SettingsViewModel = viewModel(factory = SimpleFactory { SettingsViewModel(app.container.settingsRepository) })
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var local by remember(settings) { mutableStateOf(settings) }

    GradientScreen {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = local.systemPrompt,
                        onValueChange = { local = local.copy(systemPrompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("System prompt") },
                    )
                    SliderField("Temperature", local.temperature, 0.1f..1.5f) { local = local.copy(temperature = it) }
                    SliderField("Top-k", local.topK.toFloat(), 1f..64f) { local = local.copy(topK = it.toInt()) }
                    SliderField("Top-p", local.topP, 0.1f..1.0f) { local = local.copy(topP = it) }
                    SliderField("Context length", local.contextLength.toFloat(), 2048f..16384f) {
                        local = local.copy(contextLength = (it.toInt() / 512) * 512)
                    }
                    SliderField("Thread count", local.threadCount.toFloat(), 0f..8f) { local = local.copy(threadCount = it.toInt()) }
                    ToggleField("Keep screen on during generation", local.keepScreenOnDuringGeneration) {
                        local = local.copy(keepScreenOnDuringGeneration = it)
                    }
                    OutlinedTextField(
                        value = local.huggingFaceToken,
                        onValueChange = { local = local.copy(huggingFaceToken = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional Hugging Face token") },
                    )
                    androidx.compose.material3.Button(onClick = { viewModel.save(local) }) {
                        Text("Save settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderField(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ToggleField(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
