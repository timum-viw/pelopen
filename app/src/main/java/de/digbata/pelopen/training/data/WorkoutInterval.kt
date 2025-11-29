package de.digbata.pelopen.training.data

import kotlinx.serialization.Serializable

/**
 * Represents a single interval in a workout plan
 */
@Serializable
data class WorkoutInterval(
    val intervalNumber: Int,
    val name: String,
    val durationSeconds: Int,
    val targetCadence: TargetRange,
    val targetResistance: TargetRange,
    val notes: String? = null
)
