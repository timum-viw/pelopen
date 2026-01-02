package de.digbata.pelopen.training.data

/**
 * Complete session performance summary
 */
data class SessionPerformance(
    val trainingSession: TrainingSession,
    val actualDurationSeconds: Int,
    val intervals: List<IntervalPerformance>,
    val overallCadenceFit: Float, // weighted average (longer intervals count more)
    val overallResistanceFit: Float,
    val overallAveragePower: Float,
    val overallMaxPower: Float,
    val totalPowerGenerated: Float,
    val planDifficultyAssessment: PlanDifficultyAssessment // overall assessment
)

