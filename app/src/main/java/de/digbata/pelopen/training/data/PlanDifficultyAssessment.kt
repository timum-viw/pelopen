package de.digbata.pelopen.training.data

/**
 * Assessment of whether the training plan was appropriate for the user's fitness level
 */
enum class PlanDifficultyAssessment {
    TOO_EASY,      // User consistently overshot targets
    TOO_HARD,      // User consistently below targets
    APPROPRIATE,   // User mostly within targets
    MIXED          // Some intervals too easy, some too hard
}

