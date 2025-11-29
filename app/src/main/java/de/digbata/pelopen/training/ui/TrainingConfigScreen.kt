package de.digbata.pelopen.training.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.digbata.pelopen.training.data.TrainingSession
import de.digbata.pelopen.training.data.WorkoutPlan
import java.text.SimpleDateFormat
import java.util.*

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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Training Session",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        when {
            uiState.isLoading -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                    Text("Loading workout plan…")
                }
            }
            uiState.errorMessage != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val durationSeconds = selectedDurationMinutes * 60
                        viewModel.fetchWorkoutPlan(durationSeconds, selectedIntensity)
                    }) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxSize()) {
                    // Left Column: Create new session
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Create a new session", style = MaterialTheme.typography.titleLarge)

                        // Duration selection
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select Duration",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    QuickDurationButton(30, selectedDurationMinutes == 30, { selectedDurationMinutes = 30 }, Modifier.weight(1f))
                                    QuickDurationButton(45, selectedDurationMinutes == 45, { selectedDurationMinutes = 45 }, Modifier.weight(1f))
                                    QuickDurationButton(60, selectedDurationMinutes == 60, { selectedDurationMinutes = 60 }, Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("${selectedDurationMinutes} minutes")
                                Slider(
                                    value = selectedDurationMinutes.toFloat(),
                                    onValueChange = { selectedDurationMinutes = it.toInt() },
                                    valueRange = 15f..90f,
                                    steps = 14,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Intensity selection
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Select Intensity",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IntensityButton("Low", 3, selectedIntensity == 3, { selectedIntensity = 3 }, Modifier.weight(1f))
                                    IntensityButton("Mid", 6, selectedIntensity == 6, { selectedIntensity = 6 }, Modifier.weight(1f))
                                    IntensityButton("High", 8, selectedIntensity == 8, { selectedIntensity = 8 }, Modifier.weight(1f))
                                }
                            }
                        }
                        
                        Spacer(Modifier.weight(1f))

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

                    // Divider
                    Divider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                    )

                    // Right Column: Saved sessions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Or repeat a previous session", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.savedSessions.isNotEmpty()) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.savedSessions) { session ->
                                    SessionItem(session = session, onClick = { onStartSession(session.workoutPlan) })
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No saved sessions yet.")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(session: TrainingSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.workoutPlan.name ?: "Unnamed Workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${session.workoutPlan.totalDurationSeconds / 60} min - Intensity ${session.workoutPlan.intensityLevel}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(session.sessionStartTime)),
                style = MaterialTheme.typography.bodySmall
            )
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
