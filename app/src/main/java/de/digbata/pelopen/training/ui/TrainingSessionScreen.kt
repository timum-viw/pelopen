package de.digbata.pelopen.training.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.digbata.pelopen.training.TargetStatus
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import de.digbata.pelopen.training.data.WorkoutInterval
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import de.digbata.pelopen.training.TrainingSessionState
import de.digbata.pelopen.training.TrainingSessionViewModel
import de.digbata.pelopen.training.data.WorkoutPlan
import de.digbata.pelopen.training.data.TrainingSession
import com.spop.peloton.sensors.interfaces.SensorInterface
import de.digbata.pelopen.training.ViewModelFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Main training session screen showing timer, progress, and targets
 */
@Composable
fun TrainingSessionScreen(
    sensorInterface: SensorInterface,
    workoutPlan: WorkoutPlan,
    onEndSession: (TrainingSession) -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: TrainingSessionViewModel = viewModel(factory = ViewModelFactory(application, sensorInterface))
    
    // Initialize session when workout plan is provided
    LaunchedEffect(workoutPlan) {
        viewModel.startSession(workoutPlan)
    }
    
    val sessionState by viewModel.sessionState.collectAsState()
    val activeState = sessionState as? TrainingSessionState.Active
    val totalRemainingTime = activeState?.totalRemainingTimeSeconds ?: 0L
    val intervalRemainingTime = activeState?.currentIntervalRemainingSeconds ?: 0L
    val currentInterval = activeState?.currentInterval
    val nextInterval = activeState?.nextInterval
    val previousInterval = activeState?.previousInterval
    val cadenceStatus = activeState?.cadenceStatus ?: TargetStatus.WithinRange
    val resistanceStatus = activeState?.resistanceStatus ?: TargetStatus.WithinRange
    val sessionProgress = activeState?.sessionProgress ?: 0f
    val showIntervalNotification = activeState?.showIntervalChangeNotification ?: false
    val sessionData = activeState?.sessionData ?: emptyList()
    val cadence = activeState?.currentCadence ?: 0f
    val resistance = activeState?.currentResistance ?: 0f
    val power = activeState?.currentPower ?: 0f

    val isPaused = activeState?.isPaused ?: false
    val intervals = workoutPlan.intervals
    
    // Handle back navigation
    val isSessionActive = sessionState is TrainingSessionState.Active
    var showExitDialog by remember { mutableStateOf(false) }
    
    BackHandler(enabled = isSessionActive) {
        showExitDialog = true
    }
    
    // Handle session completion
    LaunchedEffect(sessionState) {
        val state = sessionState as? TrainingSessionState.Completed
        state?.session?.let { session ->
            onEndSession(session)
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
            // Training Session Name
            workoutPlan?.let { plan ->
                Text(
                    text = plan.name ?: plan.workoutId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
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
            
            // Interval Dots - One dot for each interval
            if (intervals.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    intervals.forEachIndexed { index, interval ->
                        val isCurrentInterval = interval == currentInterval
                        Box(
                            modifier = Modifier
                                .size(if (isCurrentInterval) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrentInterval) 
                                        Color(0xFF4CAF50) // Green for current interval
                                    else 
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                        )
                        if (index < intervals.size - 1) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
            
            // Previous, Current and Next Interval - Side by Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Previous Interval
                val hasPreviousInterval = previousInterval != null
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(if (hasPreviousInterval) 0.5f else 0.0f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .alpha(0.6f)
                    ) {
                        val textColor = if (hasPreviousInterval)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy()
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.0f)
                        Text(
                            text = "Previous Interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        previousInterval?.let { prev ->
                            Text(
                                text = prev.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Duration: ${formatTime(prev.durationSeconds.toLong())}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            prev.notes?.let { notes ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } ?: Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Current Interval Info
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Current Interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        currentInterval?.let { interval ->
                            Text(
                                text = interval.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Time: ${formatTime(intervalRemainingTime)}",
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
                
                // Next Interval Preview
                val hasNextInterval = nextInterval != null
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(if(hasNextInterval) 0.5f else 0.0f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .alpha(0.6f)
                    ) {
                        val textColor = if (hasNextInterval)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy()
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.0f)
                        Text(
                            text = "Next Interval",
                            style = MaterialTheme.typography.titleSmall,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        nextInterval?.let { next ->
                            Text(
                                text = next.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Duration: ${formatTime(next.durationSeconds.toLong())}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            next.notes?.let { notes ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } ?: Text(
                            text = "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Cadence and Resistance Display - Side by Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cadence Display
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Cadence",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val interval: WorkoutInterval? = currentInterval
                        if (interval != null) {
                            Text(
                                text = "${interval.targetCadence.min.toInt()}-${interval.targetCadence.max.toInt()} ${interval.targetCadence.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${cadence.toInt()} RPM",
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
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Resistance",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val interval = currentInterval
                        if (interval != null) {
                            Text(
                                text = "${interval.targetResistance.min.toInt()}-${interval.targetResistance.max.toInt()} ${interval.targetResistance.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(text = "—", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${resistance.toInt()} %",
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
                
                // Power Display
                Card(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Power",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${power.toInt()} W",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
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
        
        // Exit confirmation dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("End Training Session?") },
                text = { Text("Your progress will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.endSession(completed = false)
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
    val duration = seconds.seconds
    return duration.toComponents { minutes, seconds, _ ->
        String.format("%02d:%02d", minutes, seconds)
    }
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
