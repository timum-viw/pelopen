package de.digbata.pelopen.training.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.digbata.pelopen.training.data.WorkoutPlan

/**
 * Screen for configuring training session (duration and intensity selection)
 */
@Composable
fun TrainingConfigScreen(
    onStartSession: (WorkoutPlan) -> Unit = {},
    viewModel: TrainingConfigViewModel = viewModel()
) {
    var selectedDurationMinutes by remember { mutableStateOf(30) }
    var selectedIntensity by remember { mutableStateOf(3) } // Low=3, Mid=6, High=8

    val uiState by viewModel.uiState.collectAsState()

    // Effect to handle navigation when a plan is successfully fetched
    LaunchedEffect(uiState.fetchedPlan) {
        uiState.fetchedPlan?.let {
            onStartSession(it)
            viewModel.onPlanHandled() // Reset the event
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Training Session",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
                Text("Loading workout plan…")
            }
            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = {
                    val durationSeconds = selectedDurationMinutes * 60
                    viewModel.fetchWorkoutPlan(durationSeconds, selectedIntensity)
                }) {
                    Text("Retry")
                }
            }
            else -> {
                // Duration selection
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Duration",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Quick selection buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickDurationButton(
                                minutes = 30,
                                selected = selectedDurationMinutes == 30,
                                onClick = { selectedDurationMinutes = 30 },
                                modifier = Modifier.weight(1f)
                            )
                            QuickDurationButton(
                                minutes = 45,
                                selected = selectedDurationMinutes == 45,
                                onClick = { selectedDurationMinutes = 45 },
                                modifier = Modifier.weight(1f)
                            )
                            QuickDurationButton(
                                minutes = 60,
                                selected = selectedDurationMinutes == 60,
                                onClick = { selectedDurationMinutes = 60 },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Slider
                        Text("${selectedDurationMinutes} minutes")
                        Slider(
                            value = selectedDurationMinutes.toFloat(),
                            onValueChange = { selectedDurationMinutes = it.toInt() },
                            valueRange = 15f..90f,
                            steps = 14, // 15, 20, 25, ..., 90
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Intensity selection
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select Intensity",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Discrete intensity options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IntensityButton(
                                label = "Low",
                                intensity = 3,
                                selected = selectedIntensity == 3,
                                onClick = { selectedIntensity = 3 },
                                modifier = Modifier.weight(1f)
                            )
                            IntensityButton(
                                label = "Mid",
                                intensity = 6,
                                selected = selectedIntensity == 6,
                                onClick = { selectedIntensity = 6 },
                                modifier = Modifier.weight(1f)
                            )
                            IntensityButton(
                                label = "High",
                                intensity = 8,
                                selected = selectedIntensity == 8,
                                onClick = { selectedIntensity = 8 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Start button
                Button(
                    onClick = {
                        val durationSeconds = selectedDurationMinutes * 60
                        viewModel.fetchWorkoutPlan(durationSeconds, selectedIntensity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text("Start Training", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickDurationButton(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (minutes) {
        30 -> "30 min"
        45 -> "45 min"
        60 -> "60 min"
        else -> "$minutes min"
    }

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntensityButton(
    label: String,
    intensity: Int, // Keep for future use
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // intensity parameter kept for future use
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
    )
}
