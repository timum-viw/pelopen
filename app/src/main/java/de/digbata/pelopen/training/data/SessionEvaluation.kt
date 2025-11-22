package de.digbata.pelopen.training.data

/**
 * Complete evaluation of a training session including plan fit assessment and recommendations
 */
data class SessionEvaluation(
    val planDifficultyAssessment: PlanDifficultyAssessment, // Computed from data
    val cadenceFit: Float, // Weighted average (0-100%)
    val resistanceFit: Float,
    val intervalsTooEasy: Int,
    val intervalsTooHard: Int,
    val intervalsAppropriate: Int,
    val problematicIntervals: List<Int>, // Interval indices with issues
    val recommendations: List<String> // Suggestions based on analysis
)

