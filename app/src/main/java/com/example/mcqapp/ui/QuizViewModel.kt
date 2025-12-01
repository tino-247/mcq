package com.example.mcqapp.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.mcqapp.data.AppDatabase
import com.example.mcqapp.data.Question
import com.example.mcqapp.data.QuestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class QuizViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: QuestionRepository
    private val _currentQuestions = MutableLiveData<List<Question>>()
    val currentQuestions: LiveData<List<Question>> = _currentQuestions

    private val _currentQuestionIndex = MutableLiveData<Int>()
    val currentQuestionIndex: LiveData<Int> = _currentQuestionIndex

    private val _quizFinished = MutableLiveData<Boolean>()
    val quizFinished: LiveData<Boolean> = _quizFinished

    private val _score = MutableLiveData<Int>()
    val score: LiveData<Int> = _score

    val allCategories: LiveData<List<String>>
    fun getSubcategories(category: String) = repository.getSubcategoriesForCategory(category)

    // To provide feedback to the UI about the import process
    private val _importStatus = MutableLiveData<Pair<Boolean, String?>>() // Pair<Success, ErrorMessage>
    val importStatus: LiveData<Pair<Boolean, String?>> = _importStatus

    private val _dbImportStatus = MutableLiveData<Pair<Boolean, String?>>()
    val dbImportStatus: LiveData<Pair<Boolean, String?>> = _dbImportStatus

    init {
        val questionDao = AppDatabase.getDatabase(application).questionDao()
        repository = QuestionRepository(questionDao, application.applicationContext)
        _score.value = 0
        allCategories = repository.allCategories

        // Populate DB from CSV on first launch or if DB is empty
        viewModelScope.launch {
            repository.populateDatabaseFromCsvIfNeeded()
        }
    }

    fun startQuiz(
        category: String? = null,
        subCategory: String? = null,
        onlyWeak: Boolean = false,
        recentStreakThreshold: Int = 3 // Add default here
    ) {
        viewModelScope.launch {
            val questions = repository.getQuizQuestions(
                category = category,
                subCategory = subCategory,
                onlyWeak = onlyWeak,
                recentStreakThreshold = recentStreakThreshold // Pass it to repository
            )
            _currentQuestions.value = questions.shuffled()
            // ... (rest of the method)
            _currentQuestionIndex.value = 0
            _quizFinished.value = false
            _score.value = 0
            if (questions.isEmpty()) {
                _quizFinished.value = true
            }
        }
    }


    fun startUnansweredQuizForSubCategory(category: String, subCategory: String) {
        viewModelScope.launch {
            Log.d("QuizVM", "Starting UNANSWERED quiz for $category - $subCategory")
            val questions = repository.getUnansweredQuizQuestions(category, subCategory)
            Log.d("QuizVM", "UNANSWERED questions fetched: ${questions.size}")
            _currentQuestions.value = questions.shuffled() // Shuffle, or not, based on preference
            _currentQuestionIndex.value = 0
            _quizFinished.value = false
            _score.value = 0
            if (questions.isEmpty()) {
                Log.d("QuizVM", "No UNANSWERED questions found, finishing quiz immediately.")
                _quizFinished.value = true
            }
        }
    }

    fun startIncorrectlyAnsweredQuizForSubCategory(category: String, subCategory: String) {
        viewModelScope.launch {
            Log.d("QuizVM", "Starting INCORRECTLY_ANSWERED quiz for $category - $subCategory")
            val questions = repository.getIncorrectlyAnsweredQuizQuestions(category, subCategory)
            Log.d("QuizVM", "INCORRECTLY_ANSWERED questions fetched: ${questions.size}")
            _currentQuestions.value = questions.shuffled()
            _currentQuestionIndex.value = 0
            _quizFinished.value = false
            _score.value = 0
            if (questions.isEmpty()) {
                Log.d("QuizVM", "No INCORRECTLY_ANSWERED questions found, finishing quiz immediately.")
                _quizFinished.value = true
            }
        }
    }


    fun getCurrentQuestion(): Question? {
        return currentQuestions.value?.getOrNull(currentQuestionIndex.value ?: -1)
    }

    fun answerQuestion(chosenAnswer: String) { // chosenAnswer is "A", "B", "C", or "D"
        val question = getCurrentQuestion() ?: return
        val isCorrect = question.correctAnswer.equals(chosenAnswer, ignoreCase = true)

        if (isCorrect) {
            _score.value = (_score.value ?: 0) + 1
        }

        viewModelScope.launch {
            repository.updateQuestionStats(question, chosenAnswer, isCorrect)
        }

        // Move to next question or finish quiz
        val nextIndex = (currentQuestionIndex.value ?: -1) + 1
        if (nextIndex < (currentQuestions.value?.size ?: 0)) {
            _currentQuestionIndex.value = nextIndex
        } else {
            _quizFinished.value = true
        }
    }

    suspend fun getAllQuestionsForExport(): List<Question> { // New method
        return repository.getAllQuestions() // Assuming repository.getAllQuestions() fetches all
    }

    suspend fun getQuestionById(id: Int): Question? {
        return repository.getQuestionById(id)
    }

    fun clearDatabase() { // New method
        viewModelScope.launch {
            repository.clearDatabase()
            // After clearing, we might want to re-populate from CSV if that's the desired behavior,
            // or leave it empty until the next populateDatabaseFromCsvIfNeeded() call.
            // For now, let's just clear it. The CSV will repopulate on next app start if empty.
        }
    }

    fun importDatabaseFromCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.clearAndImportFromCsvUri(uri)
                // If your LiveData for questions/categories doesn't auto-update from DB changes,
                // you might need to trigger a manual refresh here.
                // e.g., by re-fetching or posting to the LiveData sources.
                // Forcing populateDatabaseFromCsvIfNeeded to check again (it won't run if DB not empty)
                // is not the right way. The repository's LiveData should ideally react.
                // If not, you might need a method in repository to signal a refresh or re-fetch.
                _importStatus.postValue(Pair(true, null))
            } catch (e: Exception) {
                Log.e("QuizVM", "Import failed", e)
                _importStatus.postValue(Pair(false, e.message ?: "Unknown error during import."))
            }
        }
    }

    fun startOverallUnansweredQuiz() {
        viewModelScope.launch {
            Log.d("QuizVM", "Starting OVERALL UNANSWERED quiz")
            val questions = repository.getAllUnansweredQuizQuestions() // Call new repo method
            Log.d("QuizVM", "OVERALL UNANSWERED questions fetched: ${questions.size}")
            _currentQuestions.value = questions.shuffled()
            // ... (rest of quiz setup logic, same as other start methods)
            _currentQuestionIndex.value = 0
            _quizFinished.value = false
            _score.value = 0
            if (questions.isEmpty()) {
                _quizFinished.value = true
            }
        }
    }


    fun importSqliteDatabaseFile(sourceUri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) { // Perform on IO thread
            var success = false
            var errorMessage: String? = null
            try {
                // 1. Close current DB instance
                AppDatabase.closeAndClearInstance()
                Log.d("QuizVM_ImportDB", "Current DB instance closed.")

                // 2. Define target path and delete old DB files
                val targetDbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
                val targetDbWalFile = File(targetDbFile.parent, AppDatabase.DATABASE_NAME + "-wal")
                val targetDbShmFile = File(targetDbFile.parent, AppDatabase.DATABASE_NAME + "-shm")

                if (targetDbFile.exists()) {
                    if (!targetDbFile.delete()) Log.e("QuizVM_ImportDB", "Failed to delete old DB file.")
                    else Log.d("QuizVM_ImportDB", "Old DB file deleted.")
                }
                if (targetDbWalFile.exists()) {
                    if (!targetDbWalFile.delete()) Log.e("QuizVM_ImportDB", "Failed to delete old WAL file.")
                    else Log.d("QuizVM_ImportDB", "Old WAL file deleted.")
                }
                if (targetDbShmFile.exists()) {
                    if (!targetDbShmFile.delete()) Log.e("QuizVM_ImportDB", "Failed to delete old SHM file.")
                    else Log.d("QuizVM_ImportDB", "Old SHM file deleted.")
                }

                // 3. Copy selected DB file to app's database location
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(targetDbFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        Log.d("QuizVM_ImportDB", "New DB file copied to: ${targetDbFile.absolutePath}")
                    }
                } ?: run {
                    throw IOException("Failed to open input stream for selected DB file.")
                }

                // 4. Re-initialize Room (next call to AppDatabase.getDatabase will do this)
                // To verify, we can try to get an instance.
                // This will also trigger migrations if the imported DB has an older version.
                try {
                    val newDb = AppDatabase.getDatabase(context.applicationContext)
                    // Perform a simple query to ensure it opens and is valid
                    newDb.query("SELECT 1 FROM questions LIMIT 1", null) // Or any valid simple query
                    Log.d("QuizVM_ImportDB", "New DB instance successfully initialized and queried.")
                    success = true
                } catch (e: Exception) {
                    Log.e("QuizVM_ImportDB", "Error re-initializing or querying new DB", e)
                    errorMessage = "Error opening imported DB: ${e.message}. It might be corrupted or have an incompatible schema."
                    // Attempt to delete the bad copy
                    targetDbFile.delete()
                }

            } catch (e: Exception) {
                Log.e("QuizVM_ImportDB", "Error during SQLite DB import process", e)
                errorMessage = e.message ?: "Unknown error during DB import."
            }

            withContext(Dispatchers.Main) {
                _dbImportStatus.value = Pair(success, errorMessage)
            }
        }
    }
}