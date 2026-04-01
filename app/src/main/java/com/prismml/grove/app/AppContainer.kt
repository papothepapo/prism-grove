package com.prismml.grove.app

import android.content.Context
import com.prismml.grove.data.chat.ChatRepository
import com.prismml.grove.data.chat.ConversationRepository
import com.prismml.grove.data.db.AppDatabase
import com.prismml.grove.data.model.ModelRepository
import com.prismml.grove.data.settings.SettingsRepository
import com.prismml.grove.runtime.BonsaiRuntime

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    private val runtime = BonsaiRuntime.getInstance(context)
    val settingsRepository = SettingsRepository(context)
    val conversationRepository = ConversationRepository(
        conversationDao = database.conversationDao(),
        messageDao = database.messageDao(),
    )
    val modelRepository = ModelRepository(
        context = context,
        dao = database.modelDao(),
        runtime = runtime,
        settingsRepository = settingsRepository,
    )
    val chatRepository = ChatRepository(
        conversationRepository = conversationRepository,
        modelRepository = modelRepository,
        settingsRepository = settingsRepository,
        runtime = runtime,
    )
}
