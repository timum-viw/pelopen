package de.digbata.pelopen.training.data

import kotlinx.serialization.Serializable

/**
 * Complete workout plan received from the service
 */
@Serializable
data class WorkoutPlan(
    val workoutId: String,
    val name: String? = null,
    val description: String? = null,
    val totalDurationSeconds: Int,
    val intensityLevel: Int,
    val intervals: List<WorkoutInterval>,
    val metadata: WorkoutMetadata
)
