package de.digbata.pelopen.training.data

/**
 * Represents a single interval in a workout plan
 */
data class WorkoutInterval(
    val intervalNumber: Int,
    val name: String,
    val durationSeconds: Int,
    val targetCadence: TargetRange,
    val targetResistance: TargetRange,
    val notes: String? = null
)

