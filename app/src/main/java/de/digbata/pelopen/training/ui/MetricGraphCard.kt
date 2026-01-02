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

        fun scalePoint(point: DataPoint): Pair<Float, Float> {
            val x = size.width * ((point.x - xRange.start) / xRangeValue)
            val y = size.height * (1 - ((point.y - yMin) / yRange))
            return x to y
        }

        // Start path at the first point
        val (startX, startY) = scalePoint(data.first())
        path.moveTo(startX, startY)

        // Use cubicTo for a smooth curve
        for (i in 0 until data.size - 1) {
            val p0 = data.getOrElse(i - 1) { data[i] } // Previous point or current if first
            val p1 = data[i] // Current point
            val p2 = data[i + 1] // Next point
            val p3 = data.getOrElse(i + 2) { data[i+1] } // Point after next or next if last

            val (p0x, p0y) = scalePoint(p0)
            val (p1x, p1y) = scalePoint(p1)
            val (p2x, p2y) = scalePoint(p2)
            val (p3x, p3y) = scalePoint(p3)

            // Catmull-Rom spline calculation for control points
            val cp1x = p1x + (p2x - p0x) / 6f
            val cp1y = p1y + (p2y - p0y) / 6f
            val cp2x = p2x - (p3x - p1x) / 6f
            val cp2y = p2y - (p3y - p1y) / 6f

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
