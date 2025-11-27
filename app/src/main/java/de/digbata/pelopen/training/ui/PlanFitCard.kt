package de.digbata.pelopen.training.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.digbata.pelopen.training.data.PlanDifficultyAssessment

/**
 * Card displaying overall plan fit assessment
 */
@Composable
fun PlanFitCard(
    assessment: PlanDifficultyAssessment,
    cadenceFit: Float,
    resistanceFit: Float,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, assessmentText) = when (assessment) {
        PlanDifficultyAssessment.APPROPRIATE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Appropriate"
        )
        PlanDifficultyAssessment.TOO_EASY -> Triple(
            Color(0xFFFFF3E0), // Light orange
            Color(0xFFE65100), // Dark orange
            "Too Easy"
        )
        PlanDifficultyAssessment.TOO_HARD -> Triple(
            Color(0xFFFFEBEE), // Light red
            Color(0xFFC62828), // Dark red
            "Too Hard"
        )
        PlanDifficultyAssessment.MIXED -> Triple(
            Color(0xFFFFF9C4), // Light yellow
            Color(0xFFF57F17), // Dark yellow
            "Mixed"
        )
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Plan Fit Assessment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            
            Divider(color = contentColor.copy(alpha = 0.3f))
            
            // Assessment badge
            Surface(
                color = contentColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = assessmentText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            
            // Fit percentages
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Cadence Fit",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${String.format("%.1f", cadenceFit)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Resistance Fit",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${String.format("%.1f", resistanceFit)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

