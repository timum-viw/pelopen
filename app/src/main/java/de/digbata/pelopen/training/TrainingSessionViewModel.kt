package de.digbata.pelopen.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.digbata.pelopen.training.data.*
import de.digbata.pelopen.training.network.TrainingPlanRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sealed class representing the state of a training session
 */
sealed class TrainingSessionState {
    object Idle : TrainingSessionState()
    object Loading : TrainingSessionState()
    object Configuring : TrainingSessionState()
    data class Active(
        val workoutPlan: WorkoutPlan,
        val currentIntervalIndex: Int,
        val isPaused: Boolean
    ) : TrainingSessionState()
    object Completed : TrainingSessionState()
    data class Error(val message: String) : TrainingSessionState()
}

/**
 * ViewModel for managing training session state and timer
 */
class TrainingSessionViewModel(
    private val repository: TrainingPlanRepository = TrainingPlanRepository()
) : ViewModel() {
    
    private val _sessionState = MutableStateFlow<TrainingSessionState>(TrainingSessionState.Idle)
    val sessionState: StateFlow<TrainingSessionState> = _sessionState.asStateFlow()
    
    private val _totalRemainingTimeSeconds = MutableStateFlow(0L)
    val totalRemainingTimeSeconds: StateFlow<Long> = _totalRemainingTimeSeconds.asStateFlow()
    
    private val _currentIntervalRemainingSeconds = MutableStateFlow(0L)
    val currentIntervalRemainingSeconds: StateFlow<Long> = _currentIntervalRemainingSeconds.asStateFlow()
    
    private val _currentInterval = MutableStateFlow<WorkoutInterval?>(null)
    val currentInterval: StateFlow<WorkoutInterval?> = _currentInterval.asStateFlow()
    
    private val _nextInterval = MutableStateFlow<WorkoutInterval?>(null)
    val nextInterval: StateFlow<WorkoutInterval?> = _nextInterval.asStateFlow()
    
    private val _cadenceStatus = MutableStateFlow<TargetStatus>(TargetStatus.WithinRange)
    val cadenceStatus: StateFlow<TargetStatus> = _cadenceStatus.asStateFlow()
    
    private val _resistanceStatus = MutableStateFlow<TargetStatus>(TargetStatus.WithinRange)
    val resistanceStatus: StateFlow<TargetStatus> = _resistanceStatus.asStateFlow()
    
    private val _sessionProgress = MutableStateFlow(0f)
    val sessionProgress: StateFlow<Float> = _sessionProgress.asStateFlow()
    
    private val _showIntervalChangeNotification = MutableStateFlow(false)
    val showIntervalChangeNotification: StateFlow<Boolean> = _showIntervalChangeNotification.asStateFlow()
    
    // Timer state
    private var sessionStartTime: Long = 0
    private var intervalStartTime: Long = 0
    private var pausedElapsedTime: Long = 0
    private var intervalPausedTime: Long = 0
    private var isPaused: Boolean = false
    private var timerJob: Job? = null
    private var dataCollectionJob: Job? = null
    private var currentWorkoutPlan: WorkoutPlan? = null
    private var currentIntervalIndex: Int = 0
    
    // Sensor values
    private var currentCadence: Float = 0f
    private var currentResistance: Float = 0f
    
    // Time-series data collection
    private val sessionDataPoints = mutableListOf<SessionDataPoint>()
    
    /**
     * Start a new training session
     */
    fun startSession(durationSeconds: Int, intensity: Int) {
        viewModelScope.launch {
            _sessionState.value = TrainingSessionState.Loading
            Timber.d("Starting training session: duration=$durationSeconds, intensity=$intensity")
            
            repository.fetchWorkoutPlan(durationSeconds, intensity)
                .onSuccess { workoutPlan ->
                    Timber.d("Workout plan loaded: ${workoutPlan.intervals.size} intervals, total duration=${workoutPlan.totalDurationSeconds}s")
                    currentWorkoutPlan = workoutPlan
                    currentIntervalIndex = 0
                    
                    // Clear previous session data
                    sessionDataPoints.clear()
                    
                    // Update current and next intervals BEFORE starting timer
                    updateIntervals()
                    Timber.d("Updated intervals: current=${_currentInterval.value?.name}, next=${_nextInterval.value?.name}")
                    
                    _sessionState.value = TrainingSessionState.Active(
                        workoutPlan = workoutPlan,
                        currentIntervalIndex = 0,
                        isPaused = false
                    )
                    
                    // Start timer AFTER intervals are set
                    startTimer(workoutPlan.totalDurationSeconds)
                    // Start data collection
                    startDataCollection()
                    Timber.d("Timer started for ${workoutPlan.totalDurationSeconds}s")
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load workout plan")
                    _sessionState.value = TrainingSessionState.Error(error.message ?: "Failed to load workout plan")
                }
        }
    }
    
    /**
     * Start the timer
     */
    private fun startTimer(totalDurationSeconds: Int) {
        sessionStartTime = System.currentTimeMillis()
        intervalStartTime = System.currentTimeMillis()
        isPaused = false
        pausedElapsedTime = 0
        intervalPausedTime = 0
        
        // Set initial remaining time immediately
        _totalRemainingTimeSeconds.value = totalDurationSeconds.toLong()
        _sessionProgress.value = 0f
        
        // Set initial interval remaining time
        _currentInterval.value?.let { workoutInterval ->
            _currentIntervalRemainingSeconds.value = workoutInterval.durationSeconds.toLong()
        }
        
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second
                
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    val remaining = (totalDurationSeconds.toLong() * 1000) - elapsed
                    _totalRemainingTimeSeconds.value = maxOf(0, remaining / 1000)
                    
                    // Calculate session progress for progress bar
                    val progress = (elapsed.toFloat() / (totalDurationSeconds.toLong() * 1000)).coerceIn(0f, 1f)
                    _sessionProgress.value = progress
                    
                    // Calculate current interval remaining time
                    _currentInterval.value?.let { workoutInterval ->
                        val intervalElapsed = System.currentTimeMillis() - intervalStartTime
                        val intervalRemaining = (workoutInterval.durationSeconds * 1000) - intervalElapsed
                        _currentIntervalRemainingSeconds.value = maxOf(0, intervalRemaining / 1000)
                        
                        // Trigger interval transition immediately when time reaches 0
                        if (intervalRemaining <= 0) {
                            transitionToNextInterval()
                            // Show notification about interval change
                            _showIntervalChangeNotification.value = true
                        }
                    }
                    
                    // Check if session is complete
                    if (remaining <= 0) {
                        endSession()
                        break
                    }
                }
                // When isPaused is true, timer stops completely - no time calculations
            }
        }
    }
    
    /**
     * Update current and next intervals based on current index
     */
    private fun updateIntervals() {
        val workoutPlan = currentWorkoutPlan ?: run {
            Timber.w("updateIntervals called but no workout plan available")
            return
        }
        val intervals = workoutPlan.intervals
        
        if (currentIntervalIndex < intervals.size) {
            val workoutInterval = intervals[currentIntervalIndex]
            _currentInterval.value = workoutInterval
            Timber.d("Updated current interval to index $currentIntervalIndex: ${workoutInterval.name}, duration=${workoutInterval.durationSeconds}s")
            
            // Set initial interval remaining time
            _currentIntervalRemainingSeconds.value = workoutInterval.durationSeconds.toLong()
            
            // Set next interval
            if (currentIntervalIndex + 1 < intervals.size) {
                _nextInterval.value = intervals[currentIntervalIndex + 1]
                Timber.d("Updated next interval: ${intervals[currentIntervalIndex + 1].name}")
            } else {
                _nextInterval.value = null
                Timber.d("No next interval - last interval")
            }
        } else {
            Timber.w("Current interval index $currentIntervalIndex is out of bounds (${intervals.size} intervals)")
        }
    }
    
    /**
     * Transition to next interval
     */
    private fun transitionToNextInterval() {
        val workoutPlan = currentWorkoutPlan ?: return
        val intervals = workoutPlan.intervals
        
        if (currentIntervalIndex + 1 < intervals.size) {
            currentIntervalIndex++
            intervalStartTime = System.currentTimeMillis()
            updateIntervals()
            
            // Update state
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = workoutPlan,
                    currentIntervalIndex = currentIntervalIndex,
                    isPaused = isPaused
                )
            }
        } else {
            // No more intervals, session complete
            endSession()
        }
    }
    
    /**
     * Pause the session
     */
    fun pauseSession() {
        if (!isPaused) {
            isPaused = true
            pausedElapsedTime = System.currentTimeMillis() - sessionStartTime
            intervalPausedTime = System.currentTimeMillis() - intervalStartTime
            
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = currentState.workoutPlan,
                    currentIntervalIndex = currentState.currentIntervalIndex,
                    isPaused = true
                )
            }
        }
    }
    
    /**
     * Resume the session
     */
    fun resumeSession() {
        if (isPaused) {
            isPaused = false
            // Adjust start times to account for paused duration
            sessionStartTime = System.currentTimeMillis() - pausedElapsedTime
            intervalStartTime = System.currentTimeMillis() - intervalPausedTime
            
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = currentState.workoutPlan,
                    currentIntervalIndex = currentState.currentIntervalIndex,
                    isPaused = false
                )
            }
        }
    }
    
    /**
     * Start data collection - samples sensor values every 1 second
     */
    private fun startDataCollection() {
        dataCollectionJob?.cancel()
        dataCollectionJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Sample every 1 second
                
                if (!isPaused && _sessionState.value is TrainingSessionState.Active) {
                    val currentInterval = _currentInterval.value
                    if (currentInterval != null) {
                        val sessionElapsed = System.currentTimeMillis() - sessionStartTime
                        val intervalElapsed = System.currentTimeMillis() - intervalStartTime
                        val intervalElapsedSeconds = (intervalElapsed / 1000).toInt()
                        
                        val dataPoint = SessionDataPoint(
                            timestamp = sessionElapsed,
                            cadence = currentCadence,
                            resistance = currentResistance,
                            intervalIndex = currentIntervalIndex,
                            intervalElapsedSeconds = intervalElapsedSeconds
                        )
                        
                        sessionDataPoints.add(dataPoint)
                        Timber.v("Collected data point: cadence=${currentCadence}, resistance=${currentResistance}, interval=${currentIntervalIndex}")
                    }
                }
            }
        }
    }
    
    /**
     * End the session
     */
    fun endSession() {
        timerJob?.cancel()
        timerJob = null
        dataCollectionJob?.cancel()
        dataCollectionJob = null
        _sessionState.value = TrainingSessionState.Completed
    }
    
    /**
     * Update sensor values and compare with targets
     */
    fun updateSensorValues(cadence: Float, resistance: Float) {
        currentCadence = cadence
        currentResistance = resistance
        
        val currentInterval = _currentInterval.value
        if (currentInterval != null) {
            _cadenceStatus.value = compareValue(
                actual = cadence,
                targetMin = currentInterval.targetCadence.min,
                targetMax = currentInterval.targetCadence.max
            )
            
            _resistanceStatus.value = compareValue(
                actual = resistance,
                targetMin = currentInterval.targetResistance.min,
                targetMax = currentInterval.targetResistance.max
            )
        }
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
     * Get session summary data
     */
    fun getSessionSummary(): Pair<Int, Int>? {
        val workoutPlan = currentWorkoutPlan ?: return null
        val durationMinutes = (workoutPlan.totalDurationSeconds / 60).toInt()
        val intervalsCompleted = currentIntervalIndex + 1
        return Pair(durationMinutes, intervalsCompleted)
    }
    
    /**
     * Get complete session performance data with aggregated statistics
     */
    fun getSessionPerformance(): SessionPerformance? {
        val workoutPlan = currentWorkoutPlan ?: return null
        val sessionEndTime = System.currentTimeMillis()
        val actualDurationSeconds = ((sessionEndTime - sessionStartTime) / 1000).toInt()
        
        if (sessionDataPoints.isEmpty()) {
            Timber.w("No data points collected for session")
            return null
        }
        
        // Group data points by interval index
        val intervalsByIndex = sessionDataPoints.groupBy { it.intervalIndex }
        
        // Calculate performance for each interval
        val intervalPerformances = workoutPlan.intervals.mapIndexed { index, interval ->
            val intervalDataPoints = intervalsByIndex[index] ?: emptyList()
            calculateIntervalPerformance(interval, intervalDataPoints, index)
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
            workoutPlan = workoutPlan,
            sessionStartTime = sessionStartTime,
            sessionEndTime = sessionEndTime,
            actualDurationSeconds = actualDurationSeconds,
            intervals = intervalPerformances,
            overallCadenceFit = overallCadenceFit,
            overallResistanceFit = overallResistanceFit,
            totalDataPoints = sessionDataPoints.size,
            planDifficultyAssessment = planDifficultyAssessment
        )
    }
    
    /**
     * Calculate performance metrics for a single interval
     */
    private fun calculateIntervalPerformance(
        interval: WorkoutInterval,
        dataPoints: List<SessionDataPoint>,
        @Suppress("UNUSED_PARAMETER") intervalIndex: Int
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
    
    /**
     * Dismiss interval change notification
     */
    fun dismissIntervalNotification() {
        _showIntervalChangeNotification.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        dataCollectionJob?.cancel()
        sessionDataPoints.clear()
    }
}

