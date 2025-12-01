package com.example.mcqapp.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.example.mcqapp.data.AppDatabase
import com.example.mcqapp.data.Question
import com.example.mcqapp.data.QuestionRepository
import com.example.mcqapp.ui.models.StatisticListItem
import kotlinx.coroutines.launch

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: QuestionRepository
    private val _rawQuestions: LiveData<List<Question>>

    val groupedStatistics: MediatorLiveData<List<StatisticListItem>> = MediatorLiveData()

    init {
        val questionDao = AppDatabase.getDatabase(application).questionDao()
        repository = QuestionRepository(questionDao, application.applicationContext)
        _rawQuestions = repository.allQuestionsLiveData

        groupedStatistics.addSource(_rawQuestions) { questions ->
            questions?.let {
                processAndGroupStatistics(it)
            }
        }
    }

    private fun processAndGroupStatistics(questions: List<Question>) {
        val resultList = mutableListOf<StatisticListItem>()
        if (questions.isEmpty()) {
            // Still add summary if there are no questions, showing zeros
            resultList.add(
                StatisticListItem.AllQuestionsSummary(
                    totalQuestions = 0,
                    totalDistinctQuestionsAnswered = 0,
                    totalAttempts = 0,
                    totalCorrectAttempts = 0,
                    overallAverageCorrectness = 0f
                )
            )
            groupedStatistics.value = resultList
            return
        }

        // --- Calculate Overall Summary First ---
        var overallTotalQuestions = questions.size
        var overallDistinctQuestionsAnswered = 0
        var overallTotalAttempts = 0
        var overallTotalCorrectAttempts = 0

        questions.forEach { q ->
            if (q.timesAnswered > 0) {
                overallDistinctQuestionsAnswered++
            }
            overallTotalAttempts += q.timesAnswered
            overallTotalCorrectAttempts += q.timesCorrect
        }

        val overallAverageCorrectnessValue = if (overallTotalAttempts > 0) {
            overallTotalCorrectAttempts.toFloat() / overallTotalAttempts.toFloat()
        } else {
            0f
        }

        resultList.add(
            StatisticListItem.AllQuestionsSummary(
                totalQuestions = overallTotalQuestions,
                totalDistinctQuestionsAnswered = overallDistinctQuestionsAnswered,
                totalAttempts = overallTotalAttempts,
                totalCorrectAttempts = overallTotalCorrectAttempts,
                overallAverageCorrectness = overallAverageCorrectnessValue
            )
        )
        // --- Overall Summary Done ---

        // Group questions by Category, then by SubCategory (existing logic)
        val groupedByCatThenSubCat = questions.groupBy { it.category }
            .mapValues { entry -> entry.value.groupBy { it.subCategory } }

        groupedByCatThenSubCat.toSortedMap().forEach { (categoryName, subCategoryMap) ->
            resultList.add(StatisticListItem.CategoryHeader(categoryName))

            subCategoryMap.toSortedMap().forEach { (subCategoryName, questionsInSubCategory) ->
                var sumOfTimesAnsweredAllQuestionsInSub = 0
                var totalCorrectAttemptsInSub = 0
                var distinctQuestionsAnsweredInSub = 0
                var sumOfRecentStreaks = 0 // For averageRecentStreak
                var questionsWithStreaks = 0

                questionsInSubCategory.forEach { q ->
                    sumOfTimesAnsweredAllQuestionsInSub += q.timesAnswered
                    totalCorrectAttemptsInSub += q.timesCorrect
                    if (q.timesAnswered > 0) {
                        distinctQuestionsAnsweredInSub++
                    }
                    if (q.timesCorrectRecent > 0) {
                        sumOfRecentStreaks += q.timesCorrectRecent
                        questionsWithStreaks++
                    }
                }

                val averageCorrectness = if (sumOfTimesAnsweredAllQuestionsInSub > 0) {
                    totalCorrectAttemptsInSub.toFloat() / sumOfTimesAnsweredAllQuestionsInSub.toFloat()
                } else {
                    0f
                }
                val averageRecentStreakValue = if (questionsWithStreaks > 0) {
                    sumOfRecentStreaks.toFloat() / questionsWithStreaks.toFloat()
                } else {
                    0f
                }

                resultList.add(
                    StatisticListItem.SubCategoryStatistic(
                        parentCategoryName = categoryName,
                        subCategoryName = subCategoryName,
                        totalQuestions = questionsInSubCategory.size,
                        totalAnswered = distinctQuestionsAnsweredInSub,
                        totalCorrect = totalCorrectAttemptsInSub,
                        averageCorrectness = averageCorrectness,
                        averageRecentStreak = averageRecentStreakValue
                    )
                )
            }
        }
        groupedStatistics.value = resultList
    }

    fun resetStatisticsFor(category: String, subCategory: String) {
        viewModelScope.launch {
            val rowsUpdated = repository.resetStatsForSubCategory(category, subCategory)
            Log.d("StatsVM", "Reset stats for $category - $subCategory. Rows updated: $rowsUpdated")
            // No need to explicitly call processAndGroupStatistics here if _rawQuestions is observed.
            // If _rawQuestions doesn't automatically re-fetch or if the update query
            // doesn't trigger LiveData, you might need to manually refresh.
            // However, Room's LiveData usually handles updates to the table.
            // If not, you might need to re-fetch:
            // val currentQuestions = _rawQuestions.value
            // processAndGroupStatistics(currentQuestions ?: listOf())
            // Or, more simply, if your LiveData in repository emits on change:
            // _rawQuestions.value = repository.getAllQuestions() // if getAllQuestions() is suspend
        }
    }

}