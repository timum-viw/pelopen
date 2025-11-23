package de.digbata.pelopen.training

import de.digbata.pelopen.training.data.*
import timber.log.Timber

/**
 * Evaluates training session performance and generates recommendations
 */
class SessionEvaluator {
    
    /**
     * Calculate session performance from raw data
     */
    fun calculateSessionPerformance(trainingSession: TrainingSession): SessionPerformance? {
        if (trainingSession.dataPoints.isEmpty()) {
            Timber.w("No data points collected for session")
            return null
        }
        
        val sessionEndTime = trainingSession.sessionEndTime.takeIf { it > 0 } ?: System.currentTimeMillis()
        val actualDurationSeconds = ((sessionEndTime - trainingSession.sessionStartTime) / 1000).toInt() - trainingSession.pausedSeconds.toInt()
        
        // Group data points by interval index
        val intervalsByIndex = trainingSession.dataPoints.groupBy { it.intervalIndex }
        
        // Calculate performance for each interval
        val intervalPerformances = trainingSession.workoutPlan.intervals.mapIndexed { index, interval ->
            val intervalDataPoints = intervalsByIndex[index] ?: emptyList()
            calculateIntervalPerformance(interval, intervalDataPoints)
        }
        
        // Calculate overall weighted averages (longer intervals count more)
        val totalWeight = intervalPerformances.sumOf { it.actualDurationSeconds.toLong() }
        val overallCadenceFit = if (totalWeight > 0) {
            intervalPerformances.sumOf { 
                (it.cadenceTargetFit * it.actualDurationSeconds).toDouble()
            }.toFloat() / totalWeight
        } else 0f
        
        val overallResistanceFit = if (totalWeight > 0) {
            intervalPerformances.sumOf { 
                (it.resistanceTargetFit * it.actualDurationSeconds).toDouble()
            }.toFloat() / totalWeight
        } else 0f
        
        // Determine plan difficulty assessment
        val planDifficultyAssessment = assessPlanDifficulty(intervalPerformances)
        
        return SessionPerformance(
            trainingSession = trainingSession,
            actualDurationSeconds = actualDurationSeconds,
            intervals = intervalPerformances,
            overallCadenceFit = overallCadenceFit,
            overallResistanceFit = overallResistanceFit,
            planDifficultyAssessment = planDifficultyAssessment
        )
    }
    
    /**
     * Evaluate session performance and generate comprehensive evaluation
     */
    fun evaluateSession(performance: SessionPerformance): SessionEvaluation {
        val intervals = performance.intervals
        
        // Count intervals by category
        val intervalsTooEasy = intervals.count { it.wasTooEasy }
        val intervalsTooHard = intervals.count { it.wasTooHard }
        val intervalsAppropriate = intervals.count { it.wasAppropriate }
        
        // Identify problematic intervals (those that were too easy or too hard)
        val problematicIntervals = intervals.mapIndexedNotNull { index, interval ->
            if (interval.wasTooEasy || interval.wasTooHard) index else null
        }
        
        // Generate recommendations based on analysis
        val recommendations = generateRecommendations(
            performance = performance,
            intervalsTooEasy = intervalsTooEasy,
            intervalsTooHard = intervalsTooHard,
            intervalsAppropriate = intervalsAppropriate,
            problematicIntervals = problematicIntervals
        )
        
        return SessionEvaluation(
            planDifficultyAssessment = performance.planDifficultyAssessment,
            cadenceFit = performance.overallCadenceFit,
            resistanceFit = performance.overallResistanceFit,
            intervalsTooEasy = intervalsTooEasy,
            intervalsTooHard = intervalsTooHard,
            intervalsAppropriate = intervalsAppropriate,
            problematicIntervals = problematicIntervals,
            recommendations = recommendations
        )
    }
    
    /**
     * Generate recommendations based on session analysis
     */
    private fun generateRecommendations(
        performance: SessionPerformance,
        intervalsTooEasy: Int,
        intervalsTooHard: Int,
        intervalsAppropriate: Int,
        problematicIntervals: List<Int>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        val totalIntervals = performance.intervals.size
        val totalDataPoints = performance.trainingSession.dataPoints.size
        
        when (performance.planDifficultyAssessment) {
            PlanDifficultyAssessment.TOO_EASY -> {
                recommendations.add("The training plan was too easy for your fitness level.")
                recommendations.add("Consider selecting a higher intensity level for your next session.")
                if (intervalsTooEasy > totalIntervals / 2) {
                    recommendations.add("Most intervals were below your capabilities - you may be ready for more challenging workouts.")
                }
            }
            
            PlanDifficultyAssessment.TOO_HARD -> {
                recommendations.add("The training plan was too hard for your current fitness level.")
                recommendations.add("Consider selecting a lower intensity level or shorter duration for your next session.")
                if (intervalsTooHard > totalIntervals / 2) {
                    recommendations.add("Most intervals were above your current capabilities - focus on building endurance gradually.")
                }
            }
            
            PlanDifficultyAssessment.APPROPRIATE -> {
                recommendations.add("Great job! The training plan matched your fitness level well.")
                if (intervalsAppropriate == totalIntervals) {
                    recommendations.add("You stayed within target ranges throughout the entire session - excellent consistency!")
                } else {
                    recommendations.add("You maintained good performance across most intervals.")
                }
            }
            
            PlanDifficultyAssessment.MIXED -> {
                recommendations.add("The training plan had mixed difficulty - some intervals were too easy, others too hard.")
                if (intervalsTooEasy > intervalsTooHard) {
                    recommendations.add("More intervals were too easy than too hard - consider slightly increasing intensity.")
                } else if (intervalsTooHard > intervalsTooEasy) {
                    recommendations.add("More intervals were too hard than too easy - consider slightly decreasing intensity.")
                }
                recommendations.add("This could indicate varied interval types or inconsistent pacing - try to maintain steady effort.")
            }
        }
        
        // Add specific recommendations based on fit percentages
        if (performance.overallCadenceFit < 60f) {
            recommendations.add("Your cadence was frequently outside target ranges - focus on maintaining consistent pedaling rhythm.")
        }
        
        if (performance.overallResistanceFit < 60f) {
            recommendations.add("Your resistance was frequently outside target ranges - work on adjusting resistance more gradually.")
        }
        
        // Add recommendations for problematic intervals
        if (problematicIntervals.isNotEmpty() && problematicIntervals.size <= 2) {
            val intervalNames = problematicIntervals.joinToString(", ") { index ->
                performance.intervals.getOrNull(index)?.interval?.name ?: "Interval ${index + 1}"
            }
            recommendations.add("Pay attention to: $intervalNames - these intervals may need adjustment in future sessions.")
        }
        
        // Positive reinforcement for good performance
        if (performance.overallCadenceFit > 80f && performance.overallResistanceFit > 80f) {
            recommendations.add("Excellent target compliance! You maintained cadence and resistance within ranges very well.")
        }
        
        return recommendations
    }
    
    /**
     * Calculate performance metrics for a single interval
     */
    private fun calculateIntervalPerformance(
        interval: WorkoutInterval,
        dataPoints: List<SessionDataPoint>
    ): IntervalPerformance {
        if (dataPoints.isEmpty()) {
            // No data for this interval - return default values
            return IntervalPerformance(
                interval = interval,
                actualDurationSeconds = interval.durationSeconds,
                dataPoints = emptyList(),
                averageCadence = 0f,
                averageResistance = 0f,
                cadenceTargetFit = 0f,
                resistanceTargetFit = 0f,
                cadenceStatusSummary = emptyMap(),
                resistanceStatusSummary = emptyMap(),
                wasTooEasy = false,
                wasTooHard = false,
                wasAppropriate = false
            )
        }
        
        // Calculate actual duration from data points
        val actualDurationSeconds = if (dataPoints.size > 1) {
            val firstPoint = dataPoints.first()
            val lastPoint = dataPoints.last()
            ((lastPoint.timestamp - firstPoint.timestamp) / 1000).toInt() + 1
        } else {
            interval.durationSeconds
        }
        
        // Calculate averages
        val averageCadence = dataPoints.map { it.cadence }.average().toFloat()
        val averageResistance = dataPoints.map { it.resistance }.average().toFloat()
        
        // Count status occurrences
        val cadenceStatusCounts = mutableMapOf<TargetStatus, Int>().apply {
            put(TargetStatus.WithinRange, 0)
            put(TargetStatus.BelowMin, 0)
            put(TargetStatus.AboveMax, 0)
        }
        val resistanceStatusCounts = mutableMapOf<TargetStatus, Int>().apply {
            put(TargetStatus.WithinRange, 0)
            put(TargetStatus.BelowMin, 0)
            put(TargetStatus.AboveMax, 0)
        }
        
        dataPoints.forEach { point ->
            // Check cadence
            val cadenceStatus = compareValue(
                actual = point.cadence,
                targetMin = interval.targetCadence.min,
                targetMax = interval.targetCadence.max
            )
            val currentCadenceCount = cadenceStatusCounts[cadenceStatus] ?: 0
            cadenceStatusCounts[cadenceStatus] = currentCadenceCount + 1
            
            // Check resistance
            val resistanceStatus = compareValue(
                actual = point.resistance,
                targetMin = interval.targetResistance.min,
                targetMax = interval.targetResistance.max
            )
            val currentResistanceCount = resistanceStatusCounts[resistanceStatus] ?: 0
            resistanceStatusCounts[resistanceStatus] = currentResistanceCount + 1
        }
        
        // Calculate compliance percentages
        val totalPoints = dataPoints.size
        val cadenceTargetFit = (cadenceStatusCounts[TargetStatus.WithinRange]?.toFloat() ?: 0f) / totalPoints * 100f
        val resistanceTargetFit = (resistanceStatusCounts[TargetStatus.WithinRange]?.toFloat() ?: 0f) / totalPoints * 100f
        
        // Determine if interval was too easy, too hard, or appropriate
        // Consider both cadence and resistance
        val cadenceAboveCount = cadenceStatusCounts[TargetStatus.AboveMax] ?: 0
        val cadenceBelowCount = cadenceStatusCounts[TargetStatus.BelowMin] ?: 0
        val cadenceWithinCount = cadenceStatusCounts[TargetStatus.WithinRange] ?: 0
        
        val resistanceAboveCount = resistanceStatusCounts[TargetStatus.AboveMax] ?: 0
        val resistanceBelowCount = resistanceStatusCounts[TargetStatus.BelowMin] ?: 0
        val resistanceWithinCount = resistanceStatusCounts[TargetStatus.WithinRange] ?: 0
        
        // Combine cadence and resistance status
        val totalAbove = cadenceAboveCount + resistanceAboveCount
        val totalBelow = cadenceBelowCount + resistanceBelowCount
        val totalWithin = cadenceWithinCount + resistanceWithinCount
        val totalChecks = totalPoints * 2 // Each point has both cadence and resistance
        
        val wasTooEasy = (totalAbove.toFloat() / totalChecks) > 0.5f // More than 50% above targets
        val wasTooHard = (totalBelow.toFloat() / totalChecks) > 0.5f // More than 50% below targets
        val wasAppropriate = !wasTooEasy && !wasTooHard && (totalWithin.toFloat() / totalChecks) > 0.4f // At least 40% within targets
        
        return IntervalPerformance(
            interval = interval,
            actualDurationSeconds = actualDurationSeconds,
            dataPoints = dataPoints,
            averageCadence = averageCadence,
            averageResistance = averageResistance,
            cadenceTargetFit = cadenceTargetFit,
            resistanceTargetFit = resistanceTargetFit,
            cadenceStatusSummary = cadenceStatusCounts.toMap(),
            resistanceStatusSummary = resistanceStatusCounts.toMap(),
            wasTooEasy = wasTooEasy,
            wasTooHard = wasTooHard,
            wasAppropriate = wasAppropriate
        )
    }
    
    /**
     * Compare actual value with target range
     */
    private fun compareValue(actual: Float, targetMin: Float, targetMax: Float): TargetStatus {
        return when {
            actual < targetMin -> TargetStatus.BelowMin
            actual > targetMax -> TargetStatus.AboveMax
            else -> TargetStatus.WithinRange
        }
    }
    
    /**
     * Assess overall plan difficulty based on interval performances
     */
    private fun assessPlanDifficulty(intervals: List<IntervalPerformance>): PlanDifficultyAssessment {
        if (intervals.isEmpty()) return PlanDifficultyAssessment.MIXED
        
        val tooEasyCount = intervals.count { it.wasTooEasy }
        val tooHardCount = intervals.count { it.wasTooHard }
        val appropriateCount = intervals.count { it.wasAppropriate }
        val totalIntervals = intervals.size
        
        val tooEasyRatio = tooEasyCount.toFloat() / totalIntervals
        val tooHardRatio = tooHardCount.toFloat() / totalIntervals
        val appropriateRatio = appropriateCount.toFloat() / totalIntervals
        
        return when {
            appropriateRatio > 0.6f -> PlanDifficultyAssessment.APPROPRIATE
            tooEasyRatio > 0.6f -> PlanDifficultyAssessment.TOO_EASY
            tooHardRatio > 0.6f -> PlanDifficultyAssessment.TOO_HARD
            else -> PlanDifficultyAssessment.MIXED
        }
    }
}

