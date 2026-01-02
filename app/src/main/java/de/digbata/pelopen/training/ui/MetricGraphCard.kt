package de.digbata.pelopen.training.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds

data class DataPoint(val x: Float, val y: Float)

@Composable
fun MetricGraphCard(
    title: String? = null,
    durationSeconds: Long = 0,
    data: List<DataPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (data.size < 2) {
                Box(
                    modifier = Modifier.height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not enough data", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val maxValue = data.maxOfOrNull { it.y } ?: 0f
                val minValue = data.minOfOrNull { it.y } ?: 0f

                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "%.0f".format(maxValue),
                        style = MaterialTheme.typography.labelSmall,
                    )

                    val xEnd = if (durationSeconds > 0) {
                        durationSeconds.toFloat()
                    } else {
                        data.maxOfOrNull { it.x } ?: 0f
                    }
                    val xStart = data.minOfOrNull { it.x } ?: 0f
                    LineGraph(
                        data = data,
                        xRange = xStart..xEnd,
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
                        xStart.toInt().seconds.toComponents { minutes, seconds, _ ->
                            Text(
                                text = "%d:%02d".format(minutes.toInt(), seconds.toInt()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        xEnd.toInt().seconds.toComponents { minutes, seconds, _ ->
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
}

@Composable
private fun LineGraph(
    data: List<DataPoint>,
    xRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) {
            // Not enough data to draw a line
            return@Canvas
        }

        val yMax = data.maxOfOrNull { it.y } ?: 0f
        val yMin = data.minOfOrNull { it.y } ?: 0f
        val yRange = (yMax - yMin).coerceAtLeast(1f)
        val xRangeValue = (xRange.endInclusive - xRange.start).coerceAtLeast(1f)

        val path = Path()

        data.forEachIndexed { index, point ->
            val x = size.width * ((point.x - xRange.start) / xRangeValue)
            val y = size.height * (1 - ((point.y - yMin) / yRange))

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
