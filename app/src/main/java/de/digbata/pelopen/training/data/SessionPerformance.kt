package de.digbata.pelopen.training.data

/**
 * Complete session performance summary
 */
data class SessionPerformance(
    val workoutPlan: WorkoutPlan,
    val sessionStartTime: Long,
    val sessionEndTime: Long,
    val actualDurationSeconds: Int,
    val intervals: List<IntervalPerformance>,
    val overallCadenceFit: Float, // weighted average (longer intervals count more)
    val overallResistanceFit: Float,
    val totalDataPoints: Int,
    val planDifficultyAssessment: PlanDifficultyAssessment // overall assessment
)

