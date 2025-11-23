package de.digbata.pelopen.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.digbata.pelopen.training.data.*
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
    private val sessionEvaluator: SessionEvaluator = SessionEvaluator()
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
    
    // Session data
    private var session: TrainingSession? = null
    
    // Timer state
    private var intervalStartTime: Long = 0
    private var pauseStartTime: Long = 0 // When current pause started
    private var intervalPauseStartTime: Long = 0 // When current interval pause started
    private var isPaused: Boolean = false
    private var timerJob: Job? = null
    private var dataCollectionJob: Job? = null
    private var currentIntervalIndex: Int = 0
    
    // Sensor values
    private var currentCadence: Float = 0f
    private var currentResistance: Float = 0f
    
    /**
     * Start a new training session with a workout plan
     */
    fun startSession(workoutPlan: WorkoutPlan) {
        viewModelScope.launch {
            Timber.d("Starting training session: ${workoutPlan.intervals.size} intervals, total duration=${workoutPlan.totalDurationSeconds}s")
            
            // Create new session
            val sessionStartTime = System.currentTimeMillis()
            session = TrainingSession(
                workoutPlan = workoutPlan,
                sessionStartTime = sessionStartTime
            )
            currentIntervalIndex = 0
            
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
    }
    
    /**
     * Start the timer
     */
    private fun startTimer(totalDurationSeconds: Int) {
        val currentSession = session ?: return
        intervalStartTime = System.currentTimeMillis()
        isPaused = false
        pauseStartTime = 0
        intervalPauseStartTime = 0
        
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
                
                val currentSession = session ?: break
                
                if (!isPaused) {
                    val now = System.currentTimeMillis()
                    val elapsed = (now - currentSession.sessionStartTime) - (currentSession.pausedSeconds * 1000)
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
        val currentSession = session ?: run {
            Timber.w("updateIntervals called but no session available")
            return
        }
        val intervals = currentSession.workoutPlan.intervals
        
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
        val currentSession = session ?: return
        val intervals = currentSession.workoutPlan.intervals
        
        if (currentIntervalIndex + 1 < intervals.size) {
            currentIntervalIndex++
            intervalStartTime = System.currentTimeMillis()
            updateIntervals()
            
            // Update state
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = currentSession.workoutPlan,
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
        val currentSession = session ?: return
        if (!isPaused) {
            isPaused = true
            pauseStartTime = System.currentTimeMillis()
            intervalPauseStartTime = System.currentTimeMillis()
            
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = currentSession.workoutPlan,
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
        val currentSession = session ?: return
        if (isPaused) {
            val now = System.currentTimeMillis()
            // Add the paused duration to the total paused time
            val pauseDuration = (now - pauseStartTime) / 1000
            currentSession.pausedSeconds += pauseDuration
            
            // Adjust interval start time to account for the pause
            val intervalPauseDuration = now - intervalPauseStartTime
            intervalStartTime += intervalPauseDuration
            
            isPaused = false
            pauseStartTime = 0
            intervalPauseStartTime = 0
            
            val currentState = _sessionState.value
            if (currentState is TrainingSessionState.Active) {
                _sessionState.value = TrainingSessionState.Active(
                    workoutPlan = currentSession.workoutPlan,
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
                
                val currentSession = session ?: break
                
                if (!isPaused && _sessionState.value is TrainingSessionState.Active) {
                    val currentInterval = _currentInterval.value
                    if (currentInterval != null) {
                        val sessionElapsed = System.currentTimeMillis() - currentSession.sessionStartTime
                        val intervalElapsed = System.currentTimeMillis() - intervalStartTime
                        val intervalElapsedSeconds = (intervalElapsed / 1000).toInt()
                        
                        val dataPoint = SessionDataPoint(
                            timestamp = sessionElapsed,
                            cadence = currentCadence,
                            resistance = currentResistance,
                            intervalIndex = currentIntervalIndex,
                            intervalElapsedSeconds = intervalElapsedSeconds
                        )
                        
                        currentSession.dataPoints.add(dataPoint)
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
        session?.end()
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
     * Get complete session performance data with aggregated statistics
     */
    fun getSessionPerformance(): SessionPerformance? {
        val currentSession = session ?: return null
        return sessionEvaluator.calculateSessionPerformance(currentSession)
    }
    
    /**
     * Get session evaluation with recommendations
     */
    fun getSessionEvaluation(): SessionEvaluation? {
        val performance = getSessionPerformance() ?: return null
        return sessionEvaluator.evaluateSession(performance)
    }
    
    /**
     * Get the current training session (if available)
     */
    fun getSession(): TrainingSession? {
        return session
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
        session?.clearDataPoints()
        session = null
    }
}

