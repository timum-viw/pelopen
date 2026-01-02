package de.digbata.pelopen.training

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spop.peloton.sensors.interfaces.SensorInterface
import de.digbata.pelopen.training.data.*
import de.digbata.pelopen.training.ui.DataPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.collections.plus

class ViewModelFactory(private val application: Application, private val sensorInterface: SensorInterface) : ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrainingSessionViewModel::class.java)) {
            return TrainingSessionViewModel(application, sensorInterface) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Sealed class representing the state of a training session
 */
sealed class TrainingSessionState {
    object Idle : TrainingSessionState()
    data class Active(
        val isPaused: Boolean = false,
        val totalRemainingTimeSeconds: Long = 0,
        val currentIntervalRemainingSeconds: Long = 0,
        val sessionProgress: Float = 0.0f,
        val currentInterval: WorkoutInterval? = null,
        val nextInterval: WorkoutInterval? = null,
        val previousInterval: WorkoutInterval? = null,
        val cadenceStatus: TargetStatus = TargetStatus.WithinRange,
        val resistanceStatus: TargetStatus = TargetStatus.WithinRange,
        val showIntervalChangeNotification: Boolean = false,
        val cadence: List<DataPoint> = emptyList(),
        val resistance: List<DataPoint> = emptyList(),
        val power: List<DataPoint> = emptyList()
    ) : TrainingSessionState()
    data class Completed(
        val session: TrainingSession? = null
    ) : TrainingSessionState()
}

/**
 * ViewModel for managing training session state and timer
 */
class TrainingSessionViewModel(
    application: Application,
    private val sensorInterface: SensorInterface
) : AndroidViewModel(application) {

    private val sessionRepository = TrainingSessionRepository(application.applicationContext)
    
    private val _sessionState = MutableStateFlow<TrainingSessionState>(TrainingSessionState.Idle)
    val sessionState: StateFlow<TrainingSessionState> = _sessionState.asStateFlow()

    // Session data
    private var session: TrainingSession? = null
    
    // Timer state
    private var pauseStartTime: Long = 0 // When current pause started
    private var timerJob: Job? = null
    private var dataCollectionJob: Job? = null

    // Sensor values
    private var currentCadence: Float = 0f
    private var currentResistance: Float = 0f
    private var currentPower: Float = 0f

    init {
        startObservingSensors()
    }

    private fun startObservingSensors() {
        viewModelScope.launch {
            sensorInterface.cadence.collect {
                val currentState = _sessionState.value as? TrainingSessionState.Active ?: return@collect
                val currentInterval = currentState.currentInterval
                currentCadence = it

                val elapsedMillis = session?.calculateTotalElapsedMillis() ?: return@collect
                val newSensorData = currentState.cadence + DataPoint(
                    elapsedMillis / 1000.0f,
                    currentCadence
                )

                val cadenceStatus = currentInterval?.let {
                    compareValue(
                        actual = currentCadence,
                        targetMin = it.targetCadence.min,
                        targetMax = it.targetCadence.max
                    )
                } ?: TargetStatus.WithinRange

                _sessionState.value = currentState.copy(
                    cadenceStatus = cadenceStatus,
                    cadence = newSensorData
                )
            }
        }
        viewModelScope.launch {
            sensorInterface.resistance.collect {
                val currentState = _sessionState.value as? TrainingSessionState.Active ?: return@collect
                val currentInterval = currentState.currentInterval
                currentResistance = it

                val elapsedMillis = session?.calculateTotalElapsedMillis() ?: return@collect
                val newSensorData = currentState.resistance + DataPoint(
                    elapsedMillis / 1000.0f,
                    currentResistance
                )

                val resistanceStatus = currentInterval?.let {
                    compareValue(
                        actual = currentResistance,
                        targetMin = it.targetResistance.min,
                        targetMax = it.targetResistance.max
                    )
                } ?: TargetStatus.WithinRange

                _sessionState.value = currentState.copy(
                    resistanceStatus = resistanceStatus,
                    resistance = newSensorData
                )
            }
        }
        viewModelScope.launch {
            sensorInterface.power.collect {
                val currentState = _sessionState.value as? TrainingSessionState.Active ?: return@collect
                currentPower = it

                val elapsedMillis = session?.calculateTotalElapsedMillis() ?: return@collect
                val newSensorData = currentState.power + DataPoint(
                    elapsedMillis / 1000.0f,
                    currentPower
                )

                _sessionState.value = currentState.copy(
                    power = newSensorData
                )
            }
        }
    }

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

            _sessionState.value = TrainingSessionState.Active(
                nextInterval = workoutPlan.intervals.firstOrNull()
            )
            transitionToNextInterval()
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
        if(session == null) return
        pauseStartTime = 0
        val currentState = _sessionState.value as? TrainingSessionState.Active ?: return
        val currentInterval = currentState.currentInterval
        _sessionState.value = currentState.copy(
            totalRemainingTimeSeconds = totalDurationSeconds.toLong(),
            currentIntervalRemainingSeconds = currentInterval?.durationSeconds?.toLong() ?: 0,
            sessionProgress = 0.0f,
            isPaused = false
        )
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000) // Update every second
                val currentState = _sessionState.value as? TrainingSessionState.Active ?: continue
                val currentSession = session ?: break
                if (!currentState.isPaused) {
                    val totalElapsedMillis = currentSession.calculateTotalElapsedMillis()
                    val remaining = (totalDurationSeconds.toLong() * 1000) - totalElapsedMillis
                    val totalRemainingTimeSeconds = maxOf(0, remaining / 1000)
                    
                    // Calculate session progress for progress bar
                    val progress = (totalElapsedMillis.toFloat() / (totalDurationSeconds.toLong() * 1000)).coerceIn(0f, 1f)

                    val intervalElapsedSeconds = if(currentState.currentInterval != null) currentSession.calculateIntervalElapsed(
                        currentState.currentInterval
                    ) else 0
                    val intervalRemaining = (currentState.currentInterval?.durationSeconds ?: 0) * 1000 - intervalElapsedSeconds
                    val currentIntervalRemainingSeconds = (maxOf(0, intervalRemaining) / 1000.0).toLong()

                    _sessionState.value = currentState.copy(
                        totalRemainingTimeSeconds = totalRemainingTimeSeconds,
                        currentIntervalRemainingSeconds = currentIntervalRemainingSeconds,
                        sessionProgress = progress,
                    )

                    // Trigger interval transition immediately when time reaches 0
                    if (intervalRemaining <= 0) {
                        transitionToNextInterval()
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
     * Transition to next interval
     */
    private fun transitionToNextInterval() {
        val currentSession = session ?: return
        val intervals = currentSession.workoutPlan.intervals
        val currentState = _sessionState.value as? TrainingSessionState.Active ?: return
        val previousInterval = currentState.currentInterval
        val currentInterval = currentState.nextInterval
        val currentIntervalIndex = intervals.indexOf(currentInterval)
        val nextInterval = intervals.getOrNull(currentIntervalIndex + 1)
        if (currentInterval != null) {
            _sessionState.value = currentState.copy(
                currentInterval = currentInterval,
                nextInterval = nextInterval,
                previousInterval = previousInterval,
                showIntervalChangeNotification = true
            )
            Timber.d("Updated intervals: current=${currentInterval.name}, next=${nextInterval?.name}")
        } else {
            endSession(completed = true)
        }
    }
    
    /**
     * Pause the session
     */
    fun pauseSession() {
        val currentState = _sessionState.value as? TrainingSessionState.Active ?: return
        if (!currentState.isPaused) {
            pauseStartTime = System.currentTimeMillis()
            _sessionState.value = currentState.copy(
                isPaused = true
            )
        }
    }
    
    /**
     * Resume the session
     */
    fun resumeSession() {
        val currentState = _sessionState.value as? TrainingSessionState.Active ?: return
        val currentSession = session ?: return
        if (currentState.isPaused) {
            val now = System.currentTimeMillis()
            val pauseDuration = (now - pauseStartTime) / 1000
            currentSession.pausedSeconds += pauseDuration
            pauseStartTime = 0
            _sessionState.value = currentState.copy(
                isPaused = false
            )
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
                val currentState = _sessionState.value as? TrainingSessionState.Active ?: continue
                if (!currentState.isPaused) {
                    val totalElapsedMillis = currentSession.calculateTotalElapsedMillis()
                    val currentIntervalIndex = currentSession.workoutPlan.intervals.indexOf(currentState.currentInterval)
                    val dataPoint = SessionDataPoint(
                        timestamp = totalElapsedMillis,
                        cadence = currentCadence,
                        resistance = currentResistance,
                        power = currentPower,
                        intervalIndex = currentIntervalIndex
                    )

                    currentSession.dataPoints.add(dataPoint)
                    Timber.v("Collected data point: cadence=${currentCadence}, resistance=${currentResistance}, interval=${currentIntervalIndex}")
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
        val currentState = _sessionState.value as? TrainingSessionState.Active ?: return
        _sessionState.value = currentState.copy(
            showIntervalChangeNotification = false
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        dataCollectionJob?.cancel()
        session?.clearDataPoints()
        session = null
    }
}
