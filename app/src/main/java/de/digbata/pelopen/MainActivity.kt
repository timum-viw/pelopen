package de.digbata.pelopen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.BackHandler
import de.digbata.pelopen.navigation.AppTab
import de.digbata.pelopen.navigation.Screen
import de.digbata.pelopen.training.TrainingSessionViewModel
import de.digbata.pelopen.training.TrainingSessionState
import de.digbata.pelopen.training.data.WorkoutPlan
import de.digbata.pelopen.training.data.TrainingSession
import de.digbata.pelopen.training.ui.SessionSummaryScreen
import de.digbata.pelopen.training.ui.TrainingConfigScreen
import de.digbata.pelopen.training.ui.TrainingSessionScreen
import de.digbata.pelopen.sensors.ui.SensorDisplayScreen
import com.spop.peloton.sensors.interfaces.DummySensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikePlusSensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.peloton.sensors.interfaces.SensorInterface
import com.spop.peloton.sensors.util.IsBikePlus
import com.spop.peloton.sensors.util.IsRunningOnPeloton

class MainActivity : ComponentActivity() {
    private var sensorInterface: SensorInterface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sensor interface
        sensorInterface = if (IsRunningOnPeloton) {
            if (IsBikePlus) {
                PelotonBikePlusSensorInterface(this).also { sensor ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onPause(owner: LifecycleOwner) {
                            try { sensor.stop() } catch (e: Exception) {}
                        }
                        override fun onDestroy(owner: LifecycleOwner) {
                            try { sensor.stop() } catch (e: Exception) {}
                            lifecycle.removeObserver(this)
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also { sensor ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onPause(owner: LifecycleOwner) {
                            try { sensor.stop() } catch (e: Exception) {}
                        }
                        override fun onDestroy(owner: LifecycleOwner) {
                            try { sensor.stop() } catch (e: Exception) {}
                            lifecycle.removeObserver(this)
                        }
                    })
                }
            }
        } else {
            // Use dummy sensor for testing on non-Peloton devices
            DummySensorInterface()
        }

        setContent {
            PelopenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainActivityContent(sensorInterface = sensorInterface!!)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop sensor early to prevent ServiceConnectionLeaked
        stopSensorInterface()
    }

    override fun onDestroy() {
        // Ensure sensor is stopped before activity is destroyed
        stopSensorInterface()
        sensorInterface = null
        super.onDestroy()
    }
    
    private fun stopSensorInterface() {
        sensorInterface?.let {
            if (it !is DummySensorInterface) {
                try {
                    when (it) {
                        is PelotonBikeSensorInterfaceV1New -> it.stop()
                        is PelotonBikePlusSensorInterface -> it.stop()
                    }
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}

@Composable
fun MainActivityContent(sensorInterface: SensorInterface) {
    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf(AppTab.SENSORS) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab navigation
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == AppTab.SENSORS,
                onClick = { selectedTab = AppTab.SENSORS },
                text = { Text("Sensors") }
            )
            Tab(
                selected = selectedTab == AppTab.TRAINING,
                onClick = { selectedTab = AppTab.TRAINING },
                text = { Text("Training") }
            )
        }
        
        // Show appropriate content based on selected tab
        when (selectedTab) {
            AppTab.SENSORS -> {
                SensorDisplayScreen(sensorInterface = sensorInterface)
            }
            AppTab.TRAINING -> {
                // Share ViewModel across all training screens
                val trainingViewModel: TrainingSessionViewModel = viewModel(key = "training_session")
                // Store selected workout plan to pass to TrainingSessionScreen
                var selectedWorkoutPlan by remember { mutableStateOf<WorkoutPlan?>(null) }
                var completedSession by remember { mutableStateOf<TrainingSession?>(null) }
                
                NavHost(
                    navController = navController,
                    startDestination = Screen.TrainingConfig.route
                ) {
                    composable(Screen.TrainingConfig.route) {
                        TrainingConfigScreen(
                            onStartSession = { workoutPlan ->
                                selectedWorkoutPlan = workoutPlan
                                navController.navigate(Screen.TrainingSession.route)
                            }
                        )
                    }
                    
                    composable(Screen.TrainingSession.route) {
                        var showExitDialog by remember { mutableStateOf(false) }
                        val sessionState by trainingViewModel.sessionState.collectAsState()
                        val isSessionActive = sessionState is TrainingSessionState.Active
                        
                        // Handle back navigation
                        BackHandler(enabled = isSessionActive) {
                            showExitDialog = true
                        }
                        
                        if (showExitDialog) {
                            AlertDialog(
                                onDismissRequest = { showExitDialog = false },
                                title = { Text("End Training Session?") },
                                text = { Text("Your progress will be lost.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        trainingViewModel.endSession()
                                        navController.popBackStack()
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
                        
                        selectedWorkoutPlan?.let { workoutPlan ->
                            TrainingSessionScreen(
                                sensorInterface = sensorInterface,
                                workoutPlan = workoutPlan,
                                viewModel = trainingViewModel,
                                onEndSession = { session: TrainingSession ->
                                    completedSession = session
                                    navController.navigate(Screen.SessionSummary.route)
                                }
                            )
                        } ?: run {
                            // If no workout plan, navigate back to config
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }
                    
                    composable(Screen.SessionSummary.route) {
                        completedSession?.let { session ->
                            SessionSummaryScreen(
                                completedSession = session,
                            onStartNewSession = {
                                navController.navigate(Screen.TrainingConfig.route) {
                                    popUpTo(Screen.TrainingConfig.route) { inclusive = true }
                                }
                            },
                            onBackToSensors = {
                                selectedTab = AppTab.SENSORS
                            }
                            )
                        } ?: run {
                            // If no completed session, navigate back to config
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PelopenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        content = content
    )
}

