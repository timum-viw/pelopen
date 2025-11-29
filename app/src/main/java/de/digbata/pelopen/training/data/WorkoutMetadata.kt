package de.digbata.pelopen.training.data

import kotlinx.serialization.Serializable

/**
 * Metadata about the workout plan
 */
@Serializable
data class WorkoutMetadata(
    val generatedAt: String,
    val algorithmVersion: String
)
