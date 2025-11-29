package de.digbata.pelopen.training.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class WorkoutPlanRepository(context: Context) {

    private val plansDir = File(context.filesDir, "workout_plans").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    fun savePlan(plan: WorkoutPlan) {
        val planFile = File(plansDir, "${plan.workoutId}.json")
        // Avoid rewriting if the plan already exists and is the same
        if (planFile.exists()) return 
        val json = Json.encodeToString(plan)
        planFile.writeText(json)
    }

    fun loadPlan(workoutId: String): WorkoutPlan? {
        val planFile = File(plansDir, "$workoutId.json")
        if (!planFile.exists()) {
            return null
        }
        val json = planFile.readText()
        return try {
            Json.decodeFromString<WorkoutPlan>(json)
        } catch (e: Exception) {
            // Log or handle error
            null
        }
    }
}