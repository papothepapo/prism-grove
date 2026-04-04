package com.prismml.grove.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prismml.grove.core.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "prism_grove_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            systemPrompt = prefs[SYSTEM_PROMPT] ?: "You are a helpful assistant.",
            temperature = prefs[TEMPERATURE] ?: 0.5f,
            topK = prefs[TOP_K] ?: 20,
            topP = prefs[TOP_P] ?: 0.9f,
            contextLength = prefs[CONTEXT_LENGTH] ?: 8192,
            threadCount = prefs[THREAD_COUNT] ?: 0,
            defaultModelId = prefs[DEFAULT_MODEL_ID]?.takeIf { it.isNotBlank() } ?: "bonsai-8b",
            keepScreenOnDuringGeneration = prefs[KEEP_SCREEN_ON] ?: true,
            huggingFaceToken = prefs[HF_TOKEN] ?: "",
        )
    }

    suspend fun update(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[SYSTEM_PROMPT] = settings.systemPrompt
            prefs[TEMPERATURE] = settings.temperature
            prefs[TOP_K] = settings.topK
            prefs[TOP_P] = settings.topP
            prefs[CONTEXT_LENGTH] = settings.contextLength
            prefs[THREAD_COUNT] = settings.threadCount
            prefs[DEFAULT_MODEL_ID] = settings.defaultModelId.orEmpty()
            prefs[KEEP_SCREEN_ON] = settings.keepScreenOnDuringGeneration
            prefs[HF_TOKEN] = settings.huggingFaceToken
        }
    }

    suspend fun setDefaultModel(modelId: String?) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_MODEL_ID] = modelId.orEmpty()
        }
    }

    companion object {
        private val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        private val TEMPERATURE = floatPreferencesKey("temperature")
        private val TOP_K = intPreferencesKey("top_k")
        private val TOP_P = floatPreferencesKey("top_p")
        private val CONTEXT_LENGTH = intPreferencesKey("context_length")
        private val THREAD_COUNT = intPreferencesKey("thread_count")
        private val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        private val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val HF_TOKEN = stringPreferencesKey("hf_token")
    }
}
