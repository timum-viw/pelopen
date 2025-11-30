package de.digbata.pelopen.navigation

/**
 * Type-safe navigation routes
 */
sealed class Screen(val route: String) {
    object TrainingConfig : Screen("training_config")
    object TrainingSession : Screen("training_session")
    object SessionSummary : Screen("session_summary")
}

/**
 * App tabs for tab navigation
 */
enum class AppTab {
    SENSORS,
    TRAINING
}

