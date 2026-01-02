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

@Composable
fun MetricGraphCard(
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