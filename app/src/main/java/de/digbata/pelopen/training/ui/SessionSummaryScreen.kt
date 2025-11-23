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
import de.digbata.pelopen.training.data.PlanDifficultyAssessment

/**
 * Screen showing session completion summary
 */
@Composable
fun SessionSummaryScreen(
    viewModel: TrainingSessionViewModel,
    onStartNewSession: () -> Unit = {},
    onBackToSensors: () -> Unit = {}
) {
    val performance = remember { viewModel.getSessionPerformance() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Session Completed",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Proof of Concept: Data Collection Stats
        if (performance != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📊 Data Collection Proof of Concept",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "Total Data Points: ${performance.trainingSession.dataPoints.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "Intervals Analyzed: ${performance.intervals.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "Actual Duration: ${performance.actualDurationSeconds / 60} min ${performance.actualDurationSeconds % 60} sec",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Overall Performance:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "Cadence Fit: ${String.format("%.1f", performance.overallCadenceFit)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "Resistance Fit: ${String.format("%.1f", performance.overallResistanceFit)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Text(
                        text = "Plan Assessment: ${performance.planDifficultyAssessment.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Show first interval details as example
                    if (performance.intervals.isNotEmpty()) {
                        val firstInterval = performance.intervals.first()
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "First Interval Example:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Name: ${firstInterval.interval.name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Data Points: ${firstInterval.dataPoints.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Avg Cadence: ${String.format("%.1f", firstInterval.averageCadence)} RPM",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Avg Resistance: ${String.format("%.1f", firstInterval.averageResistance)}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Cadence Fit: ${String.format("%.1f", firstInterval.cadenceTargetFit)}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Resistance Fit: ${String.format("%.1f", firstInterval.resistanceTargetFit)}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Status: ${if (firstInterval.wasAppropriate) "Appropriate" else if (firstInterval.wasTooEasy) "Too Easy" else if (firstInterval.wasTooHard) "Too Hard" else "Mixed"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ No Performance Data Available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Data collection may not have been active during this session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
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

