package de.digbata.pelopen.training.data

import kotlinx.serialization.Serializable

/**
 * Represents a single measurement at a point in time during a training session
 */
@Serializable
data class SessionDataPoint(
    val timestamp: Long, // milliseconds since session start
    val cadence: Float,
    val resistance: Float,
    val intervalIndex: Int,
    val intervalElapsedSeconds: Int
)
