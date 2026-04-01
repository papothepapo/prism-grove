package com.prismml.grove

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prismml.grove.app.PrismGroveApp
import com.prismml.grove.ui.AppShell
import com.prismml.grove.ui.design.PrismGroveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrismGroveTheme {
                val app = LocalContext.current.applicationContext as PrismGroveApp
                val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle(
                    initialValue = com.prismml.grove.core.AppSettings()
                )
                DisposableEffect(settings.keepScreenOnDuringGeneration) {
                    if (settings.keepScreenOnDuringGeneration) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                var lastOpenedConversationId by rememberSaveable { mutableLongStateOf(-1L) }
                AppShell(
                    app = app,
                    lastOpenedConversationId = if (lastOpenedConversationId >= 0) lastOpenedConversationId else null,
                    onConversationOpened = { lastOpenedConversationId = it },
                )
            }
        }
    }
}
