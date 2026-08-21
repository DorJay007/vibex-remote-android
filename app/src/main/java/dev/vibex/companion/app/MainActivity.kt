package dev.vibex.companion.app

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
        setContent {
            val state by model.state.collectAsState()
            CompanionTheme(appearance = state.appearance, accent = state.accent) {
                CompanionApp(state = state, model = model)
            }
        }
    }
}
