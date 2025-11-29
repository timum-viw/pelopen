package de.digbata.pelopen.training.data

import kotlinx.serialization.Serializable

/**
 * Represents a target range with min and max values
 */
@Serializable
data class TargetRange(
    val min: Float,
    val max: Float,
    val unit: String
)
