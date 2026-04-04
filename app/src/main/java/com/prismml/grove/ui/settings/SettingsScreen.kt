package com.prismml.grove.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.SimpleFactory
import com.prismml.grove.ui.design.AccentPill
import com.prismml.grove.ui.design.GlassCard
import com.prismml.grove.ui.design.GradientScreen

@Composable
fun SettingsScreen(app: PrismGroveApp) {
    val viewModel: SettingsViewModel = viewModel(factory = SimpleFactory { SettingsViewModel(app.container.settingsRepository) })
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var local by remember(settings) { mutableStateOf(settings) }

    GradientScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 22.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentPill("Generation controls")
                    Text("Tune the on-device runtime.", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "These controls mirror the chat experience instead of burying it in raw form fields. The defaults are tuned for Bonsai 8B 1-bit.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Prompting", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = local.systemPrompt,
                        onValueChange = { local = local.copy(systemPrompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("System prompt") },
                        minLines = 4,
                    )
                    OutlinedTextField(
                        value = local.huggingFaceToken,
                        onValueChange = { local = local.copy(huggingFaceToken = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Optional Hugging Face token") },
                    )
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Sampling", style = MaterialTheme.typography.titleLarge)
                    SliderField("Temperature", local.temperature, 0.1f..1.5f, 2) {
                        local = local.copy(temperature = it)
                    }
                    SliderField("Top-k", local.topK.toFloat(), 1f..64f, 0) {
                        local = local.copy(topK = it.toInt())
                    }
                    SliderField("Top-p", local.topP, 0.1f..1.0f, 2) {
                        local = local.copy(topP = it)
                    }
                    SliderField("Context length", local.contextLength.toFloat(), 2048f..16384f, 0) {
                        local = local.copy(contextLength = (it.toInt() / 512) * 512)
                    }
                    SliderField("Thread count", local.threadCount.toFloat(), 0f..8f, 0) {
                        local = local.copy(threadCount = it.toInt())
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Device behavior", style = MaterialTheme.typography.titleLarge)
                    ToggleField("Keep screen on during generation", local.keepScreenOnDuringGeneration) {
                        local = local.copy(keepScreenOnDuringGeneration = it)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentPill("Default model: ${local.defaultModelId ?: "none"}", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            Button(onClick = { viewModel.save(local) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save settings")
            }
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label: ${formatSlider(value, decimals)}", style = MaterialTheme.typography.titleMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ToggleField(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun formatSlider(value: Float, decimals: Int): String =
    if (decimals == 0) {
        value.toInt().toString()
    } else {
        "%.${decimals}f".format(value)
    }
