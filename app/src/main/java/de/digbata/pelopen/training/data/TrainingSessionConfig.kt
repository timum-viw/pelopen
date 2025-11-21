package de.digbata.pelopen.training.data

/**
 * User's selected configuration for a training session
 */
data class TrainingSessionConfig(
    val durationSeconds: Int,
    val intensity: Int // 3, 6, or 8
)

