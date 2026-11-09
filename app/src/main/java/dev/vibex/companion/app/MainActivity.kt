package dev.vibex.companion.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.vibex.companion.app.ui.CompanionApp
import dev.vibex.companion.app.ui.theme.CompanionTheme

class MainActivity : ComponentActivity() {
    private val model: CompanionModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handlePairingIntent(intent)
        setContent {
            val state by model.state.collectAsState()
            CompanionTheme(appearance = state.appearance, accent = state.accent) {
                CompanionApp(state = state, model = model)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairingIntent(intent)
    }

    private fun handlePairingIntent(intent: Intent?) {
        val raw = intent?.getStringExtra("pairing_invitation")?.takeIf { it.isNotBlank() }
            ?: intent?.dataString?.takeIf { it.startsWith("vibex-pairing:", ignoreCase = true) }
            ?: return
        intent?.removeExtra("pairing_invitation")
        model.pairFromRaw(raw)
    }
}
