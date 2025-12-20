package de.digbata.pelopen.training

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
    data class Active(
        val workoutPlan: WorkoutPlan,
        val currentIntervalIndex: Int,
        val isPaused: Boolean
    ) : TrainingSessionState()
    data class Completed(
        val session: TrainingSession? = null
    ) : TrainingSessionState()
}

/**
 * ViewModel for managing training session state and timer
 */
class TrainingSessionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val sessionRepository = TrainingSessionRepository(application.applicationContext)
    
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
    private var pauseStartTime: Long = 0 // When current pause started
    private var isPaused: Boolean = false
    private var timerJob: Job? = null
    private var dataCollectionJob: Job? = null
    private var currentIntervalIndex: Int = 0
    
    // Sensor values
    private var currentCadence: Float = 0f
    private var currentResistance: Float = 0f
    private var currentPower: Float = 0f
    
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
     * Calculate total elapsed milliseconds for the current session
     * Accounts for paused time by subtracting it from wall-clock elapsed time
     */
    private fun calculateTotalElapsedMillis(session: TrainingSession): Long {
        val now = System.currentTimeMillis()
        val totalElapsed = (now - session.sessionStartTime) - (session.pausedSeconds * 1000)
        return maxOf(0L, totalElapsed)
    }
    
    /**
     * Calculate elapsed time in the current interval based on total elapsed time
     */
    private fun calculateIntervalElapsed(totalElapsedMillis: Long, currentIntervalIndex: Int, intervals: List<WorkoutInterval>): Int {
        // Sum up durations of all previous intervals in milliseconds
        val previousIntervalsDurationMillis = intervals.take(currentIntervalIndex).sumOf { it.durationSeconds.toLong() * 1000 }
        // Elapsed time in current interval = total elapsed - previous intervals duration
        val intervalElapsedMillis = maxOf(0L, totalElapsedMillis - previousIntervalsDurationMillis)
        return (intervalElapsedMillis / 1000).toInt()
    }
    
    /**
     * Start the timer
     */
    private fun startTimer(totalDurationSeconds: Int) {
        val currentSession = session ?: return
        isPaused = false
        pauseStartTime = 0
        
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
                    val totalElapsedMillis = calculateTotalElapsedMillis(currentSession)
                    val remaining = (totalDurationSeconds.toLong() * 1000) - totalElapsedMillis
                    _totalRemainingTimeSeconds.value = maxOf(0, remaining / 1000)
                    
                    // Calculate session progress for progress bar
                    val progress = (totalElapsedMillis.toFloat() / (totalDurationSeconds.toLong() * 1000)).coerceIn(0f, 1f)
                    _sessionProgress.value = progress
                    
                    // Calculate current interval remaining time from total elapsed
                    _currentInterval.value?.let { workoutInterval ->
                        val intervalElapsedSeconds = calculateIntervalElapsed(
                            totalElapsedMillis,
                            currentIntervalIndex,
                            currentSession.workoutPlan.intervals
                        )
                        val intervalRemaining = workoutInterval.durationSeconds - intervalElapsedSeconds
                        _currentIntervalRemainingSeconds.value = maxOf(0, intervalRemaining.toLong())
                        
                        // Trigger interval transition immediately when time reaches 0
                        if (intervalRemaining <= 0) {
                            transitionToNextInterval()
                            // Show notification about interval change
                            _showIntervalChangeNotification.value = true
                        }
                    }
                    
                    // Check if session is complete
                    if (remaining <= 0) {
                        endSession(completed = true)
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
            endSession(completed = true)
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
            
            isPaused = false
            pauseStartTime = 0
            
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
                        val totalElapsedMillis = calculateTotalElapsedMillis(currentSession)
                        val intervalElapsedSeconds = calculateIntervalElapsed(
                            totalElapsedMillis,
                            currentIntervalIndex,
                            currentSession.workoutPlan.intervals
                        )
                        
                        val dataPoint = SessionDataPoint(
                            timestamp = totalElapsedMillis,
                            cadence = currentCadence,
                            resistance = currentResistance,
                            power = currentPower,
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
     * @param completed true if the session completed naturally, false if stopped early
     */
    fun endSession(completed: Boolean = false) {
        timerJob?.cancel()
        timerJob = null
        dataCollectionJob?.cancel()
        dataCollectionJob = null
        session?.let {
            it.end(completed)
            sessionRepository.saveSession(it)
        }
        _sessionState.value = TrainingSessionState.Completed(session)
    }
    
    /**
     * Update sensor values and compare with targets
     */
    fun updateSensorValues(cadence: Float, resistance: Float, power: Float) {
        currentCadence = cadence
        currentResistance = resistance
        currentPower = power
        
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
