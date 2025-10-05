package com.olinky.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.olinky.app.ui.OLinkyAppTheme
import com.olinky.app.viewmodel.OverviewViewModel
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    private val viewModel = OverviewViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OLinkyAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.state.collectAsState()
                    OverviewScreen(state = uiState)
                }
            }
        }
    }
}

@Composable
fun OverviewScreen(state: OverviewUiState) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "oLinky",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )
    }
}

class OverviewViewModel {
    val state = flowOf(OverviewUiState(statusMessage = "Preparing USB gadget environment..."))
}

data class OverviewUiState(val statusMessage: String)
