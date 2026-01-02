package de.digbata.pelopen.training.data

import de.digbata.pelopen.training.TargetStatus

/**
 * Aggregated performance data for a single interval
 */
data class IntervalPerformance(
    val interval: WorkoutInterval,
    val actualDurationSeconds: Int,
    val dataPoints: List<SessionDataPoint>,
    val averageCadence: Float,
    val averageResistance: Float,
    val averagePower: Float,
    val maxPower: Float,
    val totalPower: Float,
    val cadenceTargetFit: Float, // percentage of time within target (for plan fit analysis)
    val resistanceTargetFit: Float,
    val cadenceStatusSummary: Map<TargetStatus, Int>, // count of each status
    val resistanceStatusSummary: Map<TargetStatus, Int>,
    val wasTooEasy: Boolean, // mostly above targets
    val wasTooHard: Boolean, // mostly below targets
    val wasAppropriate: Boolean // mostly within targets
)
