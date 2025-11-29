package de.digbata.pelopen.training.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.digbata.pelopen.training.data.TrainingSession
import de.digbata.pelopen.training.data.TrainingSessionRepository
import de.digbata.pelopen.training.data.WorkoutPlan
import de.digbata.pelopen.training.data.WorkoutPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Represents the various states the UI can be in
data class TrainingConfigUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val fetchedPlan: WorkoutPlan? = null,
    val savedSessions: List<TrainingSession> = emptyList()
)

class TrainingConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val workoutPlanRepository = WorkoutPlanRepository(application.applicationContext)
    private val trainingSessionRepository = TrainingSessionRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(TrainingConfigUiState())
    val uiState: StateFlow<TrainingConfigUiState> = _uiState.asStateFlow()

    init {
        loadSavedSessions()
    }

    private fun loadSavedSessions() {
        viewModelScope.launch {
            val sessions = trainingSessionRepository.loadAllSessions()
            _uiState.update { it.copy(savedSessions = sessions) }
        }
    }

    fun fetchWorkoutPlan(durationSeconds: Int, intensity: Int) {
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isLoading = true, errorMessage = null, fetchedPlan = null) }

            workoutPlanRepository.fetchWorkoutPlan(durationSeconds, intensity)
                .onSuccess { workoutPlan ->
                    _uiState.update {
                        it.copy(isLoading = false, fetchedPlan = workoutPlan)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Failed to load workout plan")
                    }
                }
        }
    }

    /**
     * Call this after the fetched plan has been handled (e.g., after navigation)
     * to prevent re-triggering the event.
     */
    fun onPlanHandled() {
        _uiState.update { it.copy(fetchedPlan = null) }
    }
}