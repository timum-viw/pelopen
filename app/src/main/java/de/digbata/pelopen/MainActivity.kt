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
import com.spop.peloton.sensors.DeadSensorDetector
import de.digbata.pelopen.navigation.AppTab
import de.digbata.pelopen.navigation.Screen
import de.digbata.pelopen.training.TrainingSessionViewModel
import de.digbata.pelopen.training.TrainingSessionState
import de.digbata.pelopen.training.ui.SessionSummaryScreen
import de.digbata.pelopen.training.ui.TrainingConfigScreen
import de.digbata.pelopen.training.ui.TrainingSessionScreen
import com.spop.peloton.sensors.interfaces.DummySensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikePlusSensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.peloton.sensors.interfaces.SensorInterface
import com.spop.peloton.sensors.util.IsBikePlus
import com.spop.peloton.sensors.util.IsRunningOnPeloton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var sensorInterface: SensorInterface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize sensor interface
        sensorInterface = if (IsRunningOnPeloton) {
            if (IsBikePlus) {
                PelotonBikePlusSensorInterface(this).also { sensor ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            sensor.stop()
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also { sensor ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            sensor.stop()
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

    override fun onDestroy() {
        super.onDestroy()
        sensorInterface?.let {
            if (it !is DummySensorInterface) {
                // DummySensorInterface doesn't need to be stopped
                // but real interfaces do
                try {
                    (it as? PelotonBikeSensorInterfaceV1New)?.stop()
                    (it as? PelotonBikePlusSensorInterface)?.stop()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
}

@Composable
fun SensorDisplayScreen(sensorInterface: SensorInterface) {
    var power by remember { mutableStateOf(0f) }
    var cadence by remember { mutableStateOf(0f) }
    var resistance by remember { mutableStateOf(0f) }
    var speed by remember { mutableStateOf(0f) }
    var sensorStatus by remember { mutableStateOf("Initializing...") }

    LaunchedEffect(sensorInterface) {
        launch {
            sensorInterface.power.collect { powerValue ->
                power = powerValue
            }
        }
        launch {
            sensorInterface.cadence.collect { cadenceValue ->
                cadence = cadenceValue
            }
        }
        launch {
            sensorInterface.resistance.collect { resistanceValue ->
                resistance = resistanceValue
            }
        }
        launch {
            sensorInterface.speed.collect { speedValue ->
                speed = speedValue
            }
        }

        // Monitor for dead sensor
        val detector = DeadSensorDetector(sensorInterface, kotlin.coroutines.coroutineContext)
        launch {
            detector.deadSensorDetected.collect {
                sensorStatus = "Sensor not responding!"
            }
        }

        sensorStatus = if (IsRunningOnPeloton) {
            if (IsBikePlus) {
                "Connected to Peloton Bike+"
            } else {
                "Connected to Peloton Bike"
            }
        } else {
            "Using Dummy Sensor (Not on Peloton device)"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pelopen",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = sensorStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SensorCard("Power", power, "W")
        Spacer(modifier = Modifier.height(16.dp))
        SensorCard("Cadence", cadence, "RPM")
        Spacer(modifier = Modifier.height(16.dp))
        SensorCard("Resistance", resistance, "")
        Spacer(modifier = Modifier.height(16.dp))
        SensorCard("Speed", speed, "m/s")
    }
}

@Composable
fun SensorCard(label: String, value: Float, unit: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${String.format("%.1f", value)} $unit",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )
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
                NavHost(
                    navController = navController,
                    startDestination = Screen.TrainingConfig.route
                ) {
                    composable(Screen.TrainingConfig.route) {
                        TrainingConfigScreen(
                            sensorInterface = sensorInterface,
                            onStartSession = {
                                navController.navigate(Screen.TrainingSession.route)
                            }
                        )
                    }
                    
                    composable(Screen.TrainingSession.route) {
                        val viewModel: TrainingSessionViewModel = viewModel()
                        var showExitDialog by remember { mutableStateOf(false) }
                        val sessionState by viewModel.sessionState.collectAsState()
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
                                        viewModel.endSession()
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
                        
                        TrainingSessionScreen(
                            sensorInterface = sensorInterface,
                            viewModel = viewModel,
                            onEndSession = {
                                navController.navigate(Screen.SessionSummary.route)
                            }
                        )
                    }
                    
                    composable(Screen.SessionSummary.route) {
                        val viewModel: TrainingSessionViewModel = viewModel()
                        SessionSummaryScreen(
                            viewModel = viewModel,
                            onStartNewSession = {
                                navController.navigate(Screen.TrainingConfig.route) {
                                    popUpTo(Screen.TrainingConfig.route) { inclusive = true }
                                }
                            },
                            onBackToSensors = {
                                selectedTab = AppTab.SENSORS
                            }
                        )
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

