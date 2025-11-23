package de.digbata.pelopen.sensors.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spop.peloton.sensors.DeadSensorDetector
import com.spop.peloton.sensors.interfaces.SensorInterface
import com.spop.peloton.sensors.util.IsBikePlus
import com.spop.peloton.sensors.util.IsRunningOnPeloton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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

