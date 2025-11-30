package de.digbata.pelopen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.spop.peloton.sensors.interfaces.DummySensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikePlusSensorInterface
import com.spop.peloton.sensors.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.peloton.sensors.interfaces.SensorInterface
import com.spop.peloton.sensors.util.IsBikePlus
import com.spop.peloton.sensors.util.IsRunningOnPeloton
import de.digbata.pelopen.navigation.TrainingNav
import de.digbata.pelopen.navigation.AppTab
import de.digbata.pelopen.sensors.ui.SensorDisplayScreen

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
                            try {
                                sensor.stop()
                            } catch (e: Exception) {
                            }
                        }

                        override fun onDestroy(owner: LifecycleOwner) {
                            try {
                                sensor.stop()
                            } catch (e: Exception) {
                            }
                            lifecycle.removeObserver(this)
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also { sensor ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onPause(owner: LifecycleOwner) {
                            try {
                                sensor.stop()
                            } catch (e: Exception) {
                            }
                        }

                        override fun onDestroy(owner: LifecycleOwner) {
                            try {
                                sensor.stop()
                            } catch (e: Exception) {
                            }
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
                TrainingNav(
                    sensorInterface = sensorInterface
                )
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

