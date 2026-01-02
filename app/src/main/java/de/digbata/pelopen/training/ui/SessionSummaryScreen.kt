package de.digbata.pelopen.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.digbata.pelopen.training.SessionEvaluator
import de.digbata.pelopen.training.data.TrainingSession
import de.digbata.pelopen.training.data.SessionDataPoint
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen showing session completion summary
 */
@Composable
fun SessionSummaryScreen(
    completedSession: TrainingSession,
    onStartNewSession: () -> Unit = {}
) {
    val sessionEvaluator = remember { SessionEvaluator() }
    val performance = remember(completedSession) {
        sessionEvaluator.calculateSessionPerformance(completedSession)
    }
    val evaluation = remember(performance) {
        performance?.let { sessionEvaluator.evaluateSession(it) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Header Section
        HeaderSection(
            session = completedSession,
            performance = performance,
            modifier = Modifier.padding(24.dp)
        )

        // Overall Stats Cards
        if (performance != null && evaluation != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan Fit Assessment Card
                PlanFitCard(
                    assessment = performance.planDifficultyAssessment,
                    cadenceFit = performance.overallCadenceFit,
                    resistanceFit = performance.overallResistanceFit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Overall Stats Card
                OverallStatsCard(
                    performance = performance,
                    evaluation = evaluation,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Error state
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ No Performance Data Available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Data collection may not have been active during this session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Graphs
        if (completedSession.dataPoints.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val durationSeconds = (completedSession.dataPoints.lastOrNull()?.timestamp ?: 0L) / 1000
                MetricGraphCard(
                    title = "Cadence",
                    durationSeconds = durationSeconds,
                    data = completedSession.dataPoints.map { it.cadence },
                    modifier = Modifier.weight(1f)
                )
                MetricGraphCard(
                    title = "Resistance",
                    durationSeconds = durationSeconds,
                    data = completedSession.dataPoints.map { it.resistance },
                    modifier = Modifier.weight(1f)
                )
                MetricGraphCard(
                    title = "Power",
                    durationSeconds = durationSeconds,
                    data = completedSession.dataPoints.mapNotNull { it.power },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartNewSession,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start New Session")
            }
        }
    }
}

@Composable
private fun HeaderSection(
    session: TrainingSession,
    performance: de.digbata.pelopen.training.data.SessionPerformance?,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    val sessionDate = dateFormat.format(Date(session.sessionStartTime))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Session Completed",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        // Session name or ID
        if (session.workoutPlan.name != null) {
            Text(
                text = session.workoutPlan.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Date and time
        Text(
            text = sessionDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Duration
        performance?.let {
            val minutes = it.actualDurationSeconds / 60
            val seconds = it.actualDurationSeconds % 60
            Text(
                text = "Duration: ${minutes} min ${seconds} sec",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverallStatsCard(
    performance: de.digbata.pelopen.training.data.SessionPerformance,
    evaluation: de.digbata.pelopen.training.data.SessionEvaluation,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Divider()

            // Intervals completed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Intervals Completed",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${performance.intervals.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Interval breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "• Appropriate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${evaluation.intervalsAppropriate}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "• Too Easy",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = "${evaluation.intervalsTooEasy}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "• Too Hard",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = "${evaluation.intervalsTooHard}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MetricGraphCard(
    title: String,
    durationSeconds: Long = 0,
    data: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val validData = data.mapNotNull { it }

            if (validData.size < 2) {
                Box(
                    modifier = Modifier.height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not enough data", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val maxValue = validData.maxOrNull() ?: 0f
                val minValue = validData.minOrNull() ?: 0f

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "%.0f".format(maxValue),
                        style = MaterialTheme.typography.labelSmall,
                    )

                    LineGraph(
                        data = data,
                        modifier = Modifier
                            .height(80.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "%.0f".format(minValue),
                        style = MaterialTheme.typography.labelSmall,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0:00",
                            style = MaterialTheme.typography.labelSmall,
                        )

                        val minutes = durationSeconds / 60
                        val seconds = durationSeconds % 60
                        Text(
                            text = "%d:%02d".format(minutes.toInt(), seconds.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineGraph(
    data: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        val validData = data.mapNotNull { it }
        if (validData.size < 2) {
            // Not enough data to draw a line
            return@Canvas
        }

        val maxValue = validData.maxOrNull() ?: 0f
        val minValue = validData.minOrNull() ?: 0f
        val range = (maxValue - minValue).coerceAtLeast(1f)

        val path = Path()

        validData.forEachIndexed { index, value ->
            val x = size.width * (index.toFloat() / (validData.size - 1))
            val y = size.height * (1 - ((value - minValue) / range))

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}