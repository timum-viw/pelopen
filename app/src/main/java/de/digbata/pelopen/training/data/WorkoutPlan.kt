package de.digbata.pelopen.training.data

/**
 * Complete workout plan received from the service
 */
data class WorkoutPlan(
    val workoutId: String,
    val name: String? = null,
    val description: String? = null,
    val totalDurationSeconds: Int,
    val intensityLevel: Int,
    val intervals: List<WorkoutInterval>,
    val metadata: WorkoutMetadata
)

