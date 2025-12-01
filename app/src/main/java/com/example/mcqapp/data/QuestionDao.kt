package com.example.mcqapp.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<Question>)

    @Update
    suspend fun updateQuestion(question: Question)

    @Query("SELECT * FROM questions")
    fun getAllQuestionsLiveData(): LiveData<List<Question>>

    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<Question>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Int): Question?

    @Query("SELECT DISTINCT category FROM questions ORDER BY category ASC")
    fun getAllCategories(): LiveData<List<String>>

    @Query("SELECT DISTINCT subCategory FROM questions WHERE category = :category ORDER BY subCategory ASC")
    fun getSubcategoriesForCategory(category: String): LiveData<List<String>>

    // --- Querying for quiz ---
    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestions(limit: Int = 1000): List<Question> // Adjust limit as needed

    @Query("SELECT * FROM questions WHERE category = :category ORDER BY RANDOM()")
    suspend fun getRandomQuestionsByCategory(category: String): List<Question>

    //    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory ORDER BY RANDOM()")
    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory")
    suspend fun getRandomQuestionsBySubCategory(category: String, subCategory: String): List<Question>

    // For "weak questions" - questions answered but with low correctness
    // Here, weak is defined as answered at least once, and correctness ratio < 0.6 (60%)
    @Query("SELECT * FROM questions WHERE timesAnswered > 0 AND (CAST(timesCorrect AS REAL) / timesAnswered) < :threshold ORDER BY RANDOM()")
    suspend fun getWeakQuestions(threshold: Float = 0.6f): List<Question>
//    @Query("SELECT * FROM questions WHERE timesCorrectRecent < :threshold ORDER BY RANDOM()")
//    suspend fun getWeakQuestions(threshold: Int = 3): List<Question>

    @Query("SELECT * FROM questions WHERE timesAnswered > 0 AND (CAST(timesCorrect AS REAL) / timesAnswered) < :threshold ORDER BY RANDOM() LIMIT :limit")
    suspend fun getWeakQuestionsByRatio(threshold: Float = 0.6f, limit: Int = 100): List<Question> // Renamed for clarity

    // Weak questions (timesCorrectRecent < 3) from a specific category & sub-category
    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory AND timesCorrectRecent < 3 ORDER BY RANDOM()")
    suspend fun getWeakQuestionsBySubCategoryRecent(category: String, subCategory: String): List<Question>

    // Weak questions (timesCorrectRecent < 3) from a specific category (all its sub-categories)
    @Query("SELECT * FROM questions WHERE category = :category AND timesCorrectRecent < 3 ORDER BY RANDOM()")
    suspend fun getWeakQuestionsByCategoryRecent(category: String): List<Question>

    // All weak questions (timesCorrectRecent < 3) across all categories/sub-categories
    @Query("SELECT * FROM questions WHERE timesCorrectRecent < 3 ORDER BY RANDOM()")
    suspend fun getAllWeakQuestionsRecent(): List<Question>


    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionCount(): Int

    @Query("DELETE FROM questions")
    suspend fun clearAllQuestions() // New method

    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory AND timesAnswered = 0 ORDER BY RANDOM()")
    suspend fun getUnansweredQuestionsBySubCategory(category: String, subCategory: String): List<Question>

    // Incorrectly answered: answered but timesCorrect < timesAnswered (or simply not timesCorrect = timesAnswered if only answered once)
    // A simpler way: timesAnswered > 0 AND timesCorrect < timesAnswered (implies at least one incorrect)
    // OR even simpler: timesAnswered > timesCorrect
    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory AND timesAnswered > 0 AND timesCorrect < timesAnswered ORDER BY RANDOM()")
    suspend fun getIncorrectlyAnsweredQuestionsBySubCategory(category: String, subCategory: String): List<Question>

    @Query("""
        UPDATE questions
        SET timesAnswered = 0,
            timesCorrect = 0,
            timesChosenA = 0,
            timesChosenB = 0,
            timesChosenC = 0,
            timesChosenD = 0,
            timesCorrectRecent = 0
        WHERE category = :category AND subCategory = :subCategory
    """)
    suspend fun resetStatisticsForSubCategory(category: String, subCategory: String): Int

    @Query("SELECT * FROM questions WHERE timesAnswered = 0 ORDER BY RANDOM()")
    suspend fun getAllUnansweredQuestions(): List<Question>

    @Query("SELECT * FROM questions WHERE category = :category AND subCategory = :subCategory AND timesCorrectRecent < :recentStreakThreshold ORDER BY RANDOM()")
    suspend fun getWeakQuestionsBySubCategoryRecent(category: String, subCategory: String, recentStreakThreshold: Int): List<Question>

    @Query("SELECT * FROM questions WHERE category = :category AND timesCorrectRecent < :recentStreakThreshold ORDER BY RANDOM()")
    suspend fun getWeakQuestionsByCategoryRecent(category: String, recentStreakThreshold: Int): List<Question>

    @Query("SELECT * FROM questions WHERE timesCorrectRecent < :recentStreakThreshold ORDER BY RANDOM()")
    suspend fun getAllWeakQuestionsRecent(recentStreakThreshold: Int): List<Question>


}