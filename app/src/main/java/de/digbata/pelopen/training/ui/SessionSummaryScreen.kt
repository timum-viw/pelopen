package de.digbata.pelopen.training.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.digbata.pelopen.training.TrainingSessionViewModel

/**
 * Screen showing session completion summary
 */
@Composable
fun SessionSummaryScreen(
    viewModel: TrainingSessionViewModel,
    onStartNewSession: () -> Unit = {},
    onBackToSensors: () -> Unit = {}
) {
    val summary = viewModel.getSessionSummary()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Session Completed",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (summary != null) {
                    Text(
                        text = "Session completed: ${summary.first} minutes, ${summary.second} intervals",
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    Text(
                        text = "Session completed",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Buttons
        Button(
            onClick = onStartNewSession,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start New Session")
        }
        
        OutlinedButton(
            onClick = onBackToSensors,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Sensors")
        }
    }
}

