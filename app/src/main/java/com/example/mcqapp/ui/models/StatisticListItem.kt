// In ui/models/StatisticListItem.kt
package com.example.mcqapp.ui.models

sealed class StatisticListItem {
    // New data class for the overall summary
    data class AllQuestionsSummary(
        val totalQuestions: Int,
        val totalDistinctQuestionsAnswered: Int, // Distinct questions answered at least once
        val totalAttempts: Int,                  // Sum of timesAnswered for all questions
        val totalCorrectAttempts: Int,           // Sum of timesCorrect for all questions
        val overallAverageCorrectness: Float     // Based on all attempts
    ) : StatisticListItem()

    data class CategoryHeader(val categoryName: String) : StatisticListItem()
    data class SubCategoryStatistic(
        val parentCategoryName: String,
        val subCategoryName: String,
        val totalQuestions: Int,
        val totalAnswered: Int, // Distinct questions answered in this sub-category
        val totalCorrect: Int,  // Sum of correct answers in this sub-category
        val averageCorrectness: Float,
        val averageRecentStreak: Float // You had this, decide if it makes sense for overall too
    ) : StatisticListItem()
}