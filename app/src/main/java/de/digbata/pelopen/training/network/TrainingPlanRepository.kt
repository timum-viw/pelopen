package de.digbata.pelopen.training.network

import de.digbata.pelopen.training.data.TargetRange
import de.digbata.pelopen.training.data.WorkoutInterval
import de.digbata.pelopen.training.data.WorkoutMetadata
import de.digbata.pelopen.training.data.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import timber.log.Timber

/**
 * Repository for fetching training plans from the online service
 */
class TrainingPlanRepository {
    // Hardcoded service URL
    // For testing without a real service, set USE_MOCK_DATA = true
    private val USE_MOCK_DATA = false
    private val BASE_URL = "http://192.168.2.225:3001/api/v1/training-plan"
    
    /**
     * Fetch workout plan from the service
     * @param durationSeconds Duration in seconds
     * @param intensity Intensity level (3, 6, or 8)
     * @return Result containing WorkoutPlan or error
     */
    suspend fun fetchWorkoutPlan(durationSeconds: Int, intensity: Int): Result<WorkoutPlan> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                // Use mock data for testing
                if (USE_MOCK_DATA) {
                    delay(1000) // Simulate network delay
                    val mockPlan = createMockWorkoutPlan(durationSeconds, intensity)
                    return@withContext Result.success(mockPlan)
                }
                
                // Build URL with query parameters
                val urlString = "$BASE_URL?duration_seconds=$durationSeconds&intensity=$intensity"
                val url = URL(urlString)
                // Log with both Timber and Android Log for debugging
                android.util.Log.d("TrainingPlanRepo", "Fetching: $urlString")
                Timber.d("Fetching workout plan from: $urlString")
                
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000 // 10 seconds
                connection.readTimeout = 10000
                connection.doInput = true
                
                // Establish connection
                connection.connect()
                val responseCode = connection.responseCode
                Timber.d("Connected to server, response code: $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val response = inputStream.bufferedReader().use { it.readText() }
                    Timber.d("Response received: ${response.take(200)}...")
                    val jsonObject = JSONObject(response)
                    
                    // Parse JSON to WorkoutPlan
                    val workoutPlan = parseWorkoutPlan(jsonObject)
                    Timber.d("Successfully parsed workout plan with ${workoutPlan.intervals.size} intervals")
                    Result.success(workoutPlan)
                } else {
                    // Try to read error stream
                    val errorStream = connection.errorStream
                    val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                    Timber.e("HTTP error: $responseCode, response: $errorResponse")
                    Result.failure(Exception("HTTP $responseCode: $errorResponse"))
                }
            } catch (e: java.net.ConnectException) {
                Timber.e(e, "Failed to connect to server at $BASE_URL - is the server running?")
                Result.failure(Exception("Cannot connect to server. Check if server is running at $BASE_URL"))
            } catch (e: java.net.SocketTimeoutException) {
                Timber.e(e, "Connection timeout to $BASE_URL")
                Result.failure(Exception("Connection timeout. Server may be slow or unreachable."))
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch workout plan: ${e.message}")
                Result.failure(e)
            } finally {
                // Ensure connection is disconnected
                try {
                    connection?.disconnect()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Create mock workout plan for testing
     */
    private fun createMockWorkoutPlan(durationSeconds: Int, intensity: Int): WorkoutPlan {
        val intervals = mutableListOf<WorkoutInterval>()
        val intervalDuration = durationSeconds / 4 // 4 intervals
        
        // Warm-up
        intervals.add(WorkoutInterval(
            intervalNumber = 1,
            name = "Warm-up",
            durationSeconds = intervalDuration,
            targetCadence = TargetRange(80f, 90f, "rpm"),
            targetResistance = TargetRange(30f, 35f, "%"),
            notes = "Gradual warm-up to prepare for main effort"
        ))
        
        // Main effort based on intensity
        val cadenceMin = when (intensity) {
            3 -> 85f
            6 -> 90f
            8 -> 95f
            else -> 90f
        }
        val cadenceMax = cadenceMin + 10f
        val resistanceMin = when (intensity) {
            3 -> 40f
            6 -> 50f
            8 -> 60f
            else -> 50f
        }
        val resistanceMax = resistanceMin + 10f
        
        intervals.add(WorkoutInterval(
            intervalNumber = 2,
            name = "Main Effort",
            durationSeconds = intervalDuration * 2,
            targetCadence = TargetRange(cadenceMin, cadenceMax, "rpm"),
            targetResistance = TargetRange(resistanceMin, resistanceMax, "%"),
            notes = "Sustained effort at target intensity"
        ))
        
        // Cool-down
        intervals.add(WorkoutInterval(
            intervalNumber = 3,
            name = "Cool-down",
            durationSeconds = intervalDuration,
            targetCadence = TargetRange(70f, 80f, "rpm"),
            targetResistance = TargetRange(25f, 30f, "%"),
            notes = "Gradual cool-down"
        ))
        
        return WorkoutPlan(
            workoutId = "mock-${System.currentTimeMillis()}",
            totalDurationSeconds = durationSeconds,
            intensityLevel = intensity,
            intervals = intervals,
            metadata = WorkoutMetadata(
                generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()),
                algorithmVersion = "1.0-mock"
            )
        )
    }
    
    /**
     * Parse JSONObject to WorkoutPlan
     */
    private fun parseWorkoutPlan(json: JSONObject): WorkoutPlan {
        val workoutId = json.getString("workout_id")
        val totalDurationSeconds = json.getInt("total_duration_seconds")
        val intensityLevel = json.getInt("intensity_level")
        
        // Parse intervals
        val intervalsArray = json.getJSONArray("intervals")
        val intervals = mutableListOf<WorkoutInterval>()
        for (i in 0 until intervalsArray.length()) {
            val intervalJson = intervalsArray.getJSONObject(i)
            intervals.add(parseWorkoutInterval(intervalJson))
        }
        
        // Parse metadata
        val metadataJson = json.getJSONObject("metadata")
        val metadata = WorkoutMetadata(
            generatedAt = metadataJson.getString("generated_at"),
            algorithmVersion = metadataJson.getString("algorithm_version")
        )
        
        return WorkoutPlan(
            workoutId = workoutId,
            totalDurationSeconds = totalDurationSeconds,
            intensityLevel = intensityLevel,
            intervals = intervals,
            metadata = metadata
        )
    }
    
    /**
     * Parse JSONObject to WorkoutInterval
     */
    private fun parseWorkoutInterval(json: JSONObject): WorkoutInterval {
        val intervalNumber = json.getInt("interval_number")
        val name = json.getString("name")
        val durationSeconds = json.getInt("duration_seconds")
        
        // Parse target cadence
        val cadenceJson = json.getJSONObject("target_cadence")
        val targetCadence = TargetRange(
            min = cadenceJson.getDouble("min").toFloat(),
            max = cadenceJson.getDouble("max").toFloat(),
            unit = cadenceJson.getString("unit")
        )
        
        // Parse target resistance
        val resistanceJson = json.getJSONObject("target_resistance")
        val targetResistance = TargetRange(
            min = resistanceJson.getDouble("min").toFloat(),
            max = resistanceJson.getDouble("max").toFloat(),
            unit = resistanceJson.getString("unit")
        )
        
        val notes = if (json.has("notes") && !json.isNull("notes")) {
            json.getString("notes")
        } else {
            null
        }
        
        return WorkoutInterval(
            intervalNumber = intervalNumber,
            name = name,
            durationSeconds = durationSeconds,
            targetCadence = targetCadence,
            targetResistance = targetResistance,
            notes = notes
        )
    }
}

