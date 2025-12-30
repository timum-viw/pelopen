package de.digbata.pelopen.training.data

/**
 * Encapsulates core session data: workout plan, timing, and collected data points
 */
data class TrainingSession(
    val workoutPlan: WorkoutPlan,
    val sessionStartTime: Long, // Immutable - when session actually started
    var sessionEndTime: Long = 0L,
    var pausedSeconds: Long = 0L, // Total time spent paused
    var wasCompleted: Boolean = false, // True if session completed naturally, false if stopped early
    val dataPoints: MutableList<SessionDataPoint> = mutableListOf()
) {
    /**
     * Clear all collected data points
     */
    fun clearDataPoints() {
        dataPoints.clear()
    }
    
    /**
     * Mark session as ended
     * @param completed true if the session completed naturally, false if stopped early
     */
    fun end(completed: Boolean = false) {
        sessionEndTime = System.currentTimeMillis()
        wasCompleted = completed
    }

    fun calculateTotalElapsedMillis(time: Long = System.currentTimeMillis()): Long {
        val totalElapsed = (time - this.sessionStartTime) - (this.pausedSeconds * 1000)
        return maxOf(0L, totalElapsed)
    }

    fun calculateIntervalElapsed(interval: WorkoutInterval): Long {
        val totalElapsedMillis = this.calculateTotalElapsedMillis()
        val previousIntervalsDurationMillis = this.workoutPlan.intervals.takeWhile { it != interval }.sumOf { it.durationSeconds.toLong() * 1000 }
        val intervalElapsedMillis = maxOf(0L, totalElapsedMillis - previousIntervalsDurationMillis)
        return intervalElapsedMillis
    }

    fun calculateTotalPowerInKcal(): Double {
        return dataPoints.sumOf { (it.power ?: 0).toDouble() } / 4186.8
    }

}

