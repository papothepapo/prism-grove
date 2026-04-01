package com.prismml.grove.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prismml.grove.core.ModelItem
import com.prismml.grove.data.model.ModelRepository
import com.prismml.grove.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModelsUiState(
    val models: List<ModelItem> = emptyList(),
    val defaultModelId: String? = null,
    val errorMessage: String? = null,
)

class ModelsViewModel(
    private val modelRepository: ModelRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val errorMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val state: StateFlow<ModelsUiState> = combine(
        modelRepository.observeModels(),
        settingsRepository.settings,
        errorMessage,
    ) { models, settings, error ->
        ModelsUiState(models = models, defaultModelId = settings.defaultModelId, errorMessage = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelsUiState())

    fun download(modelId: String) {
        viewModelScope.launch { modelRepository.enqueueDownload(modelId) }
    }

    fun cancel(modelId: String) {
        viewModelScope.launch { modelRepository.cancelDownload(modelId) }
    }

    fun delete(modelId: String) {
        viewModelScope.launch { modelRepository.deleteModel(modelId) }
    }

    fun selectDefault(modelId: String?) {
        viewModelScope.launch { modelRepository.selectDefaultModel(modelId) }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            errorMessage.value = modelRepository.importModel(uri).exceptionOrNull()?.message
        }
    }
}
