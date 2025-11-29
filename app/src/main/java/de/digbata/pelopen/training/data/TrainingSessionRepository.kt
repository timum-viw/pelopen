package de.digbata.pelopen.training.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class SessionData(
    val workoutId: String,
    val sessionStartTime: Long,
    var sessionEndTime: Long,
    var pausedSeconds: Long,
    var wasCompleted: Boolean,
    val dataPoints: List<SessionDataPoint>
)

class TrainingSessionRepository(context: Context) {

    private val workoutPlanRepository = WorkoutPlanRepository(context)
    private val sessionsDir = File(context.filesDir, "sessions").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    fun saveSession(session: TrainingSession) {
        workoutPlanRepository.savePlan(session.workoutPlan)

        val sessionData = SessionData(
            workoutId = session.workoutPlan.workoutId,
            sessionStartTime = session.sessionStartTime,
            sessionEndTime = session.sessionEndTime,
            pausedSeconds = session.pausedSeconds,
            wasCompleted = session.wasCompleted,
            dataPoints = session.dataPoints
        )

        val sessionFile = File(sessionsDir, "${session.sessionStartTime}.json")
        val json = Json.encodeToString(sessionData)
        sessionFile.writeText(json)
    }

    fun loadSession(startTime: String): TrainingSession? {
        val sessionFile = File(sessionsDir, "$startTime.json")
        if (!sessionFile.exists()) {
            return null
        }
        val json = sessionFile.readText()
        val sessionData = try {
            Json.decodeFromString<SessionData>(json)
        } catch (e: Exception) {
            return null
        }

        val workoutPlan = workoutPlanRepository.loadPlan(sessionData.workoutId) ?: return null

        return TrainingSession(
            workoutPlan = workoutPlan,
            sessionStartTime = sessionData.sessionStartTime,
            sessionEndTime = sessionData.sessionEndTime,
            pausedSeconds = sessionData.pausedSeconds,
            wasCompleted = sessionData.wasCompleted,
            dataPoints = sessionData.dataPoints.toMutableList()
        )
    }

    fun loadAllSessions(): List<TrainingSession> {
        val sessionFiles = sessionsDir.listFiles() ?: return emptyList()
        return sessionFiles
            .filter { it.isFile && it.name.endsWith(".json") }
            .mapNotNull { file ->
                val json = file.readText()
                val sessionData = try {
                    Json.decodeFromString<SessionData>(json)
                } catch (e: Exception) {
                    null
                }

                sessionData?.let { data ->
                    workoutPlanRepository.loadPlan(data.workoutId)?.let { plan ->
                        TrainingSession(
                            workoutPlan = plan,
                            sessionStartTime = data.sessionStartTime,
                            sessionEndTime = data.sessionEndTime,
                            pausedSeconds = data.pausedSeconds,
                            wasCompleted = data.wasCompleted,
                            dataPoints = data.dataPoints.toMutableList()
                        )
                    }
                }
            }
            .sortedByDescending { it.sessionStartTime }
    }
}