package com.example.mcqapp.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import com.opencsv.CSVReaderHeaderAware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

class QuestionRepository(private val questionDao: QuestionDao, private val context: Context) {

    val allQuestionsLiveData: LiveData<List<Question>> = questionDao.getAllQuestionsLiveData()
    val allCategories: LiveData<List<String>> = questionDao.getAllCategories()

    fun getSubcategoriesForCategory(category: String): LiveData<List<String>> {
        return questionDao.getSubcategoriesForCategory(category)
    }

    suspend fun getQuestionCount(): Int {
        return questionDao.getQuestionCount()
    }

    suspend fun getQuizQuestions(
        category: String? = null,
        subCategory: String? = null,
        onlyWeak: Boolean = false,
        recentStreakThreshold: Int = 3 // Add default here too
    ): List<Question> {
        return when {
            onlyWeak -> {
                when {
                    category != null && subCategory != null -> questionDao.getWeakQuestionsBySubCategoryRecent(category, subCategory, recentStreakThreshold)
                    category != null -> questionDao.getWeakQuestionsByCategoryRecent(category, recentStreakThreshold)
                    else -> questionDao.getAllWeakQuestionsRecent(recentStreakThreshold)
                }
            }
            // ... (rest of the when statement for non-weak cases)
            category != null && subCategory != null -> questionDao.getRandomQuestionsBySubCategory(category, subCategory)
            category != null -> questionDao.getRandomQuestionsByCategory(category)
            else -> questionDao.getRandomQuestions()
        }
    }


    // For "UNANSWERED" button from statistics
    suspend fun getUnansweredQuizQuestions(category: String, subCategory: String): List<Question> {
        Log.d("Repository", "Fetching UNANSWERED for $category - $subCategory")
        return questionDao.getUnansweredQuestionsBySubCategory(category, subCategory)
    }

    // For "INCORRECT" button from statistics
    suspend fun getIncorrectlyAnsweredQuizQuestions(category: String, subCategory: String): List<Question> {
        Log.d("Repository", "Fetching INCORRECTLY_ANSWERED for $category - $subCategory")
        return questionDao.getIncorrectlyAnsweredQuestionsBySubCategory(category, subCategory)
    }

    suspend fun updateQuestionStats(question: Question, chosenAnswer: String, isCorrect: Boolean) {
        question.timesAnswered++
        if (isCorrect) {
            question.timesCorrect++
            question.timesCorrectRecent++ // Increment recent correct streak
        } else {
            question.timesCorrectRecent = 0 // Reset streak on incorrect answer
        }

        when (chosenAnswer) {
            "A" -> question.timesChosenA++
            "B" -> question.timesChosenB++
            "C" -> question.timesChosenC++
            "D" -> question.timesChosenD++
        }
        questionDao.updateQuestion(question)
    }



    suspend fun populateDatabaseFromCsvIfNeeded() {
        if (questionDao.getQuestionCount() == 0) {
            withContext(Dispatchers.IO) {
                val questions = mutableListOf<Question>()
                try {
                    context.assets.open("questions_database.csv").use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            CSVReaderHeaderAware(reader).use { csvReader ->
                                var record: Map<String, String>?
                                while (csvReader.readMap().also { record = it } != null) {
                                    record?.let {
                                        try {
                                        questions.add(
                                            Question(
                                                category = it["Kategorie"] ?: "",
                                                subCategory = it["Unter-Kategorie"] ?: "",
                                                questionNumber = it["Frage #"] ?: "",
                                                text = it["Frage"] ?: "",
                                                optionA = it["Antwort A"] ?: "",
                                                optionB = it["Antwort B"] ?: "",
                                                optionC = it["Antwort C"] ?: "",
                                                optionD = it["Antwort D"] ?: "",
                                                correctAnswer = it["Richtige Antwort"] ?: "",
                                                imageName = it["Abbildung"]?.takeIf { img -> img.isNotBlank() }
                                            )
                                        )
                                        } catch (e: Exception) {
                                            // Log error or handle it
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (questions.isNotEmpty()) {
                        questionDao.insertAll(questions)
                    }
                } catch (e: Exception) {
                    // Log error or handle it
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getAllQuestions(): List<Question> { // If not present, add it
        return questionDao.getAllQuestions()
    }

    suspend fun getQuestionById(id: Int): Question? {
        return questionDao.getQuestionById(id)
    }

    suspend fun clearDatabase() { // New method
        questionDao.clearAllQuestions()
        // Optionally, you might want to trigger a re-fetch or notify observers
        // that the data has changed significantly, though LiveData should update.
    }

    suspend fun resetStatsForSubCategory(category: String, subCategory: String): Int {
        return questionDao.resetStatisticsForSubCategory(category, subCategory)
    }

    suspend fun clearAndImportFromCsvUri(uri: Uri) {
        // Clear existing database
        questionDao.clearAllQuestions() // Assumes this is your method to DELETE all from questions table

        // Import from the given URI
        val questionsToInsert = mutableListOf<Question>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream, "UTF-8").use { reader ->
                    CSVReaderHeaderAware(reader).use { csvReader ->
                        var record: Map<String, String>?
                        while (csvReader.readMap().also { record = it } != null) {
                            record?.let {
                                try {
                                    questionsToInsert.add(
                                        Question(
                                            // id = 0, // Let Room auto-generate IDs
                                            category = it["Kategorie"] ?: "",
                                            subCategory = it["Unter-Kategorie"] ?: "",
                                            questionNumber = it["Frage #"] ?: "",
                                            text = it["Frage"] ?: "",
                                            optionA = it["Antwort A"] ?: "",
                                            optionB = it["Antwort B"] ?: "",
                                            optionC = it["Antwort C"] ?: "",
                                            optionD = it["Antwort D"] ?: "",
                                            correctAnswer = it["Richtige Antwort"] ?: "",
                                            imageName = it["Abbildung"]?.takeIf { img -> img.isNotBlank() },
                                            timesAnswered = it["TimesAnswered"]?.toIntOrNull() ?: 0,
                                            timesCorrect = it["TimesCorrect"]?.toIntOrNull() ?: 0,
                                            timesChosenA = it["TimesChosenA"]?.toIntOrNull() ?: 0,
                                            timesChosenB = it["TimesChosenB"]?.toIntOrNull() ?: 0,
                                            timesChosenC = it["TimesChosenC"]?.toIntOrNull() ?: 0,
                                            timesChosenD = it["TimesChosenD"]?.toIntOrNull() ?: 0,
                                            timesCorrectRecent = it["timesCorrectRecent"]?.toIntOrNull() ?: 0
                                        )
                                    )
                                } catch (e: Exception) {
                                    Log.e("QuestionRepository", "Error parsing a row in imported CSV: $it", e)
                                    // Optionally skip this row or stop import
                                }
                            }
                        }
                    }
                }
            }
            if (questionsToInsert.isNotEmpty()) {
                questionDao.insertAll(questionsToInsert) // Assumes insertAll replaces or handles conflicts appropriately for a full import
                Log.d("QuestionRepository", "Successfully imported ${questionsToInsert.size} questions from URI.")
            } else {
                Log.d("QuestionRepository", "No questions found in the selected CSV or error during parsing.")
            }
        } catch (e: Exception) {
            Log.e("QuestionRepository", "Error importing CSV from URI", e)
            throw e // Re-throw to be caught by ViewModel/Activity
        }
    }

    suspend fun getAllUnansweredQuizQuestions(): List<Question> { // New method for overall
        Log.d("Repository", "Fetching ALL UNANSWERED questions")
        return questionDao.getAllUnansweredQuestions()
    }


}