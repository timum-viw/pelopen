package de.digbata.pelopen.training.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.digbata.pelopen.R
import de.digbata.pelopen.training.TargetStatus
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import de.digbata.pelopen.training.data.WorkoutInterval
import androidx.activity.compose.BackHandler
import de.digbata.pelopen.training.TrainingSessionState
import de.digbata.pelopen.training.TrainingSessionViewModel
import com.spop.peloton.sensors.interfaces.SensorInterface
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

/**
 * Main training session screen showing timer, progress, and targets
 */
@Composable
fun TrainingSessionScreen(
    sensorInterface: SensorInterface,
    viewModel: TrainingSessionViewModel = viewModel(),
    onEndSession: () -> Unit = {}
) {
    var cadence by remember { mutableStateOf(0f) }
    var resistance by remember { mutableStateOf(0f) }
    
    val sessionState by viewModel.sessionState.collectAsState()
    val totalRemainingTime by viewModel.totalRemainingTimeSeconds.collectAsState()
    val intervalRemainingTime by viewModel.currentIntervalRemainingSeconds.collectAsState()
    val currentInterval by viewModel.currentInterval.collectAsState()
    val nextInterval by viewModel.nextInterval.collectAsState()
    val cadenceStatus by viewModel.cadenceStatus.collectAsState()
    val resistanceStatus by viewModel.resistanceStatus.collectAsState()
    val sessionProgress by viewModel.sessionProgress.collectAsState()
    val showIntervalNotification by viewModel.showIntervalChangeNotification.collectAsState()
    
    // Collect sensor values
    LaunchedEffect(sensorInterface) {
        launch {
            sensorInterface.cadence.collect { cadenceValue ->
                cadence = cadenceValue
                viewModel.updateSensorValues(cadence, resistance)
            }
        }
        launch {
            sensorInterface.resistance.collect { resistanceValue ->
                resistance = resistanceValue
                viewModel.updateSensorValues(cadence, resistance)
            }
        }
    }
    
    // Handle back navigation
    val isSessionActive = sessionState is TrainingSessionState.Active
    var showExitDialog by remember { mutableStateOf(false) }
    
    BackHandler(enabled = isSessionActive) {
        showExitDialog = true
    }
    
    // Handle session completion
    LaunchedEffect(sessionState) {
        if (sessionState is TrainingSessionState.Completed) {
            onEndSession()
        }
    }
    
    // Auto-dismiss interval notification
    LaunchedEffect(showIntervalNotification) {
        if (showIntervalNotification) {
            kotlinx.coroutines.delay(3000)
            viewModel.dismissIntervalNotification()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Timer Display - Large, prominent
            Text(
                text = formatTime(totalRemainingTime),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp
            )
            
            // Progress Bar
            LinearProgressIndicator(
                progress = sessionProgress,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            
            // Current Interval Info
            currentInterval?.let { interval ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = interval.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Interval Time: ${formatTime(intervalRemainingTime)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        interval.notes?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Cadence Display
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Cadence",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val interval: WorkoutInterval? = currentInterval
                    if (interval != null) {
                        Text(
                            text = "Target: ${interval.targetCadence.min.toInt()}-${interval.targetCadence.max.toInt()} ${interval.targetCadence.unit}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val cadenceUnit = interval?.targetCadence?.unit ?: "RPM"
                    Text(
                        text = "${cadence.toInt()} $cadenceUnit",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = getStatusColor(cadenceStatus)
                    )
                    Text(
                        text = getStatusText(cadenceStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = getStatusColor(cadenceStatus)
                    )
                }
            }
            
            // Resistance Display
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Resistance",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val resistanceInterval: WorkoutInterval? = currentInterval
                    if (resistanceInterval != null) {
                        Text(
                            text = "Target: ${resistanceInterval.targetResistance.min.toInt()}-${resistanceInterval.targetResistance.max.toInt()}${resistanceInterval.targetResistance.unit}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val resistanceUnit = resistanceInterval?.targetResistance?.unit ?: "%"
                    Text(
                        text = "${resistance.toInt()}$resistanceUnit",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = getStatusColor(resistanceStatus)
                    )
                    Text(
                        text = getStatusText(resistanceStatus),
                        style = MaterialTheme.typography.bodySmall,
                        color = getStatusColor(resistanceStatus)
                    )
                }
            }
            
            // Next Interval Preview
            nextInterval?.let { next ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Next Interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = next.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Cadence: ${next.targetCadence.min.toInt()}-${next.targetCadence.max.toInt()} ${next.targetCadence.unit}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Resistance: ${next.targetResistance.min.toInt()}-${next.targetResistance.max.toInt()}${next.targetResistance.unit}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeState = sessionState as? TrainingSessionState.Active
                if (activeState != null) {
                    val isPaused = activeState.isPaused
                    Button(
                        onClick = {
                            if (isPaused) {
                                viewModel.resumeSession()
                            } else {
                                viewModel.pauseSession()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                    
                    Button(
                        onClick = { showExitDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("End Session")
                    }
                }
            }
        }
        
        // Exit confirmation dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("End Training Session?") },
                text = { Text("Your progress will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.endSession()
                        onEndSession()
                        showExitDialog = false
                    }) {
                        Text("End")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Interval change notification - show Snackbar
        val intervalForNotification: WorkoutInterval? = currentInterval
        if (showIntervalNotification && intervalForNotification != null) {
            // Show Snackbar notification
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    "New interval: ${intervalForNotification.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = TimeUnit.SECONDS.toMinutes(seconds)
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

@Composable
private fun getStatusColor(status: TargetStatus): Color {
    return when (status) {
        TargetStatus.WithinRange -> MaterialTheme.colorScheme.primary
        TargetStatus.BelowMin -> MaterialTheme.colorScheme.error
        TargetStatus.AboveMax -> MaterialTheme.colorScheme.tertiary
    }
}

private fun getStatusText(status: TargetStatus): String {
    return when (status) {
        TargetStatus.WithinRange -> "Within Target"
        TargetStatus.BelowMin -> "Below Target"
        TargetStatus.AboveMax -> "Above Target"
    }
}

