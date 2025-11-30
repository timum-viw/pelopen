package de.digbata.pelopen.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spop.peloton.sensors.interfaces.SensorInterface
import de.digbata.pelopen.training.data.TrainingSession
import de.digbata.pelopen.training.data.WorkoutPlan
import de.digbata.pelopen.training.ui.SessionSummaryScreen
import de.digbata.pelopen.training.ui.TrainingConfigScreen
import de.digbata.pelopen.training.ui.TrainingSessionScreen

@Composable
fun TrainingNav(
    sensorInterface: SensorInterface
) {
    val navController = rememberNavController()

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
            selectedWorkoutPlan?.let { workoutPlan ->
                TrainingSessionScreen(
                    sensorInterface = sensorInterface,
                    workoutPlan = workoutPlan,
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
