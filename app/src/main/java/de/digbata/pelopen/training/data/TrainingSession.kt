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
}

