package com.example.mcqapp.ui

import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.Log // Import Log
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mcqapp.R
import com.example.mcqapp.SubCategoryLearningMode
import com.example.mcqapp.data.Question
import com.example.mcqapp.databinding.ActivityQuizBinding
import java.text.NumberFormat

class QuizActivity : AppCompatActivity() {

    companion object {
        const val TAG = "QuizActivity" // For logging
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_SUB_CATEGORY = "extra_sub_category"
        const val EXTRA_ONLY_WEAK = "extra_only_weak"
        const val EXTRA_LEARNING_MODE = "extra_learning_mode"
        const val EXTRA_RECENT_STREAK_THRESHOLD = "extra_recent_streak_threshold"
    }

    private lateinit var binding: ActivityQuizBinding
    private val viewModel: QuizViewModel by viewModels()
    private var answerSubmittedForCurrentQuestion = false

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        maximumFractionDigits = 0 // No decimal places for percentage
    }

    // Define the listener separately for clarity and easier attach/detach
    private val radioGroupListener = RadioGroup.OnCheckedChangeListener { group, checkedId ->
        Log.d(TAG, "RadioGroup listener fired. checkedId: $checkedId, submitted: $answerSubmittedForCurrentQuestion")
        if (!answerSubmittedForCurrentQuestion && checkedId != -1) {
            highlightCorrectAnswerOnly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val categoryFromIntent = intent.getStringExtra(EXTRA_CATEGORY)
        val subCategoryFromIntent = intent.getStringExtra(EXTRA_SUB_CATEGORY)
        val onlyWeakFromIntent = intent.getBooleanExtra(EXTRA_ONLY_WEAK, false)
        // Read the threshold, use a default if not passed (e.g., for quizzes from Statistics)
        val recentStreakThreshold = intent.getIntExtra(EXTRA_RECENT_STREAK_THRESHOLD, 3)
        val learningModeName = intent.getStringExtra(EXTRA_LEARNING_MODE)


        var effectiveLearningMode: SubCategoryLearningMode? = null
        if (learningModeName != null) {
            try {
                effectiveLearningMode = SubCategoryLearningMode.valueOf(learningModeName)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid learning mode name received: $learningModeName", e)
            }
        }

        if (effectiveLearningMode != null) {
            Log.d(TAG, "Starting quiz with mode from Stats: $effectiveLearningMode, Cat: $categoryFromIntent, SubCat: $subCategoryFromIntent")
            when (effectiveLearningMode) {
                SubCategoryLearningMode.ALL ->
                    viewModel.startQuiz(
                        category = categoryFromIntent,
                        subCategory = subCategoryFromIntent,
                        onlyWeak = false,
                        recentStreakThreshold = recentStreakThreshold // Pass it along
                    )
                SubCategoryLearningMode.UNANSWERED ->
                    viewModel.startUnansweredQuizForSubCategory(categoryFromIntent!!, subCategoryFromIntent!!) // Assuming category/subCategory are non-null for this mode
                SubCategoryLearningMode.WEAK_RECENT_STREAK ->
                    viewModel.startQuiz(
                        category = categoryFromIntent,
                        subCategory = subCategoryFromIntent,
                        onlyWeak = true, // 'onlyWeak' is true for this mode
                        recentStreakThreshold = recentStreakThreshold // Pass the threshold
                    )
                else -> {
                    Log.w(TAG, "Unhandled learning mode: $effectiveLearningMode, falling back.")
                    viewModel.startQuiz(categoryFromIntent, subCategoryFromIntent, onlyWeakFromIntent, recentStreakThreshold)
                }
            }
        } else {
            // Quiz started from MainActivity
            Log.d(TAG, "Starting quiz from MainActivity. Cat: $categoryFromIntent, SubCat: $subCategoryFromIntent, Weak: $onlyWeakFromIntent, Threshold: $recentStreakThreshold")
            viewModel.startQuiz(categoryFromIntent, subCategoryFromIntent, onlyWeakFromIntent, recentStreakThreshold)
        }

        viewModel.currentQuestionIndex.observe(this) { index ->
            Log.d(TAG, "currentQuestionIndex observed: $index")
            viewModel.currentQuestions.value?.let { questions ->
                if (index < questions.size && index >= 0) {
                    answerSubmittedForCurrentQuestion = false
                    displayQuestion(questions[index], index + 1, questions.size)
                }
            }
        }

        viewModel.quizFinished.observe(this) { finished ->
            Log.d(TAG, "quizFinished observed: $finished")
            if (finished && !answerSubmittedForCurrentQuestion) { // Check flag here too
                showQuizResults()
            }
        }

        // Set the listener initially
        binding.rgOptions.setOnCheckedChangeListener(radioGroupListener)

        binding.btnSubmitAnswer.setOnClickListener {
            Log.d(TAG, "Submit button clicked. submitted: $answerSubmittedForCurrentQuestion")
            if (!answerSubmittedForCurrentQuestion) {
                handleSubmit()
            }
            // No 'else' block needed here; LiveData should drive subsequent UI after delay
        }

        binding.btnBackToMain.setOnClickListener {
            finish()
        }
    }

    private fun getLetterForRadioButtonId(radioButtonId: Int): String? {
        return when (radioButtonId) {
            binding.rbOptionA.id -> "A"
            binding.rbOptionB.id -> "B"
            binding.rbOptionC.id -> "C"
            binding.rbOptionD.id -> "D"
            else -> null
        }
    }

    private fun getSelectedAnswerLetter(): String? {
        return getLetterForRadioButtonId(binding.rgOptions.checkedRadioButtonId)
    }

    private fun displayQuestion(question: Question, qNum: Int, totalQ: Int) {
        Log.d(TAG, "displayQuestion: ${question.text}")
        Log.d(TAG, "Q Stats - Answered: ${question.timesAnswered}, Correct: ${question.timesCorrect}")

        // ... (listener handling, clearCheck, resetOptionColors, enableRadioButtons)
        binding.rgOptions.setOnCheckedChangeListener(null)
        binding.rgOptions.clearCheck()
        binding.rgOptions.setOnCheckedChangeListener(radioGroupListener)

        resetOptionColors()
        enableRadioButtons(true)
        binding.btnSubmitAnswer.isEnabled = true
        binding.btnSubmitAnswer.text = getString(R.string.submit_answer_button)

        // --- Populate Top Row Information ---
        binding.tvFrageNummerCsv.text = question.questionNumber
        binding.tvQuestionProgress.text = getString(R.string.question_progress_short_format, qNum, totalQ) // Potentially use a shorter format

        if (question.timesAnswered > 0) {
            val correctnessRatio = question.timesCorrect.toFloat() / question.timesAnswered.toFloat()
            val statsText = "Ans: ${question.timesAnswered}, Ok: ${percentFormatter.format(correctnessRatio)}, Streak: ${question.timesCorrectRecent}"
            binding.tvQuestionIndividualStatsTop.text = statsText
            binding.tvQuestionIndividualStatsTop.visibility = View.VISIBLE
        } else {
            binding.tvQuestionIndividualStatsTop.text = "New, Streak: 0"
            binding.tvQuestionIndividualStatsTop.visibility = View.VISIBLE
        }

        // Main question text and options
        binding.tvQuestionText.text = question.text
        binding.rbOptionA.text = question.optionA
        binding.rbOptionB.text = question.optionB
        binding.rbOptionC.text = question.optionC
        binding.rbOptionD.text = question.optionD

        // Image loading
        binding.ivQuestionImage.visibility = View.GONE
        if (question.imageName != null && question.imageName.isNotBlank()) {
            // ... (your image loading logic with "image_" prefix)
            val baseImageName = question.imageName
            val resourceNameWithoutExtension = if (baseImageName.contains(".")) {
                baseImageName.substringBeforeLast(".")
            } else {
                baseImageName
            }
            val fullResourceName = "image_" + resourceNameWithoutExtension
            Log.d(TAG, "Attempting to load drawable resource: $fullResourceName")
            try {
                val resourceId = resources.getIdentifier(fullResourceName, "drawable", packageName)
                if (resourceId != 0) {
                    binding.ivQuestionImage.setImageResource(resourceId)
                    binding.ivQuestionImage.visibility = View.VISIBLE
                } else {
                    android.util.Log.w(TAG, "Image resource NOT FOUND: $fullResourceName")
                }
            } catch (e: Exception) { // Broader catch for any resource issue
                android.util.Log.e(TAG, "Error loading image: $fullResourceName", e)
            }
        }

        // Ensure correct UI elements are visible/hidden
        binding.layoutQuizFinished.visibility = View.GONE
        binding.tvFrageNummerCsv.visibility = View.VISIBLE
        binding.tvQuestionProgress.visibility = View.VISIBLE
        // binding.tvQuestionIndividualStats visibility is handled above
        binding.tvQuestionText.visibility = View.VISIBLE
        binding.rgOptions.visibility = View.VISIBLE
        binding.btnSubmitAnswer.visibility = View.VISIBLE
    }


    private fun highlightCorrectAnswerOnly() {
        Log.d(TAG, "highlightCorrectAnswerOnly called")
        val currentQuestion = viewModel.getCurrentQuestion()
        if (currentQuestion == null) {
            Log.e(TAG, "highlightCorrectAnswerOnly: currentQuestion is null!")
            return
        }
        // Convert numeric string to letter
        val correctAnswerLetter = currentQuestion.correctAnswer
        if (correctAnswerLetter == null) {
            Log.e(TAG, "highlightCorrectAnswerOnly: Could not convert numeric answer ${currentQuestion.correctAnswer} to letter.")
            return
        }
        Log.d(TAG, "Correct Answer Letter (after conversion): $correctAnswerLetter")

        resetOptionColors(excludeCorrect = true, correctLetter = correctAnswerLetter)

        val optionsMap = mapOf(
            "A" to binding.rbOptionA, "B" to binding.rbOptionB,
            "C" to binding.rbOptionC, "D" to binding.rbOptionD
        )

        val correctRadioButton = optionsMap[correctAnswerLetter]
        if (correctRadioButton == null) {
            Log.e(TAG, "highlightCorrectAnswerOnly: Could not find RadioButton for correct letter: $correctAnswerLetter")
            return
        }

        correctRadioButton.setBackgroundColor(
            ContextCompat.getColor(this, R.color.correct_answer_background)
        )
        Log.d(TAG, "Highlighted ${correctAnswerLetter} with green")
    }

    private fun handleSubmit() {
        val selectedAnswerLetter = getSelectedAnswerLetter()
        if (selectedAnswerLetter == null) {
            Toast.makeText(this, getString(R.string.please_select_answer), Toast.LENGTH_SHORT).show()
            return
        }
        answerSubmittedForCurrentQuestion = true
        Log.d(TAG, "handleSubmit: selected $selectedAnswerLetter")

        val currentQuestion = viewModel.getCurrentQuestion()
        if (currentQuestion == null) {
            Log.e(TAG, "handleSubmit: currentQuestion is null!")
            // Potentially show an error and don't proceed
            Toast.makeText(this, "Error: Could not load current question.", Toast.LENGTH_SHORT).show()
            // You might want to disable buttons or navigate away here if this happens
            enableRadioButtons(false)
            binding.btnSubmitAnswer.isEnabled = false
            return
        }

        val actualCorrectLetter = currentQuestion.correctAnswer
        if (actualCorrectLetter == null) {
            Log.e(TAG, "handleSubmit: Could not determine correct answer letter from: ${currentQuestion.correctAnswer}")
            Toast.makeText(this, "Error in question data (correct answer).", Toast.LENGTH_SHORT).show()
            enableRadioButtons(false)
            binding.btnSubmitAnswer.isEnabled = false
            return
        }

        val isCorrect = actualCorrectLetter.equals(selectedAnswerLetter, ignoreCase = true)
        Log.d(TAG, "handleSubmit: isCorrect = $isCorrect (comparing $selectedAnswerLetter with $actualCorrectLetter)")

        applyFullFeedbackHighlight(actualCorrectLetter, selectedAnswerLetter, isCorrect)

        binding.btnSubmitAnswer.isEnabled = false // Still good to disable briefly to prevent double taps
        enableRadioButtons(false)

        Log.d(TAG, "Immediate: calling answerQuestion for $selectedAnswerLetter")
        viewModel.answerQuestion(selectedAnswerLetter)
    }

    private fun applyFullFeedbackHighlight(correctAnswerLetterFromSubmit: String, chosenAnswerLetter: String, wasCorrectlyAnswered: Boolean) {
        Log.d(TAG, "applyFullFeedbackHighlight: Correct: $correctAnswerLetterFromSubmit, Chosen: $chosenAnswerLetter, WasCorrect: $wasCorrectlyAnswered")
        val optionsMap = mapOf(
            "A" to binding.rbOptionA, "B" to binding.rbOptionB,
            "C" to binding.rbOptionC, "D" to binding.rbOptionD
        )


        optionsMap.forEach { (optionLetter, radioButton) ->
            if (optionLetter.equals(correctAnswerLetterFromSubmit, ignoreCase = true)) {
                radioButton.setBackgroundColor(ContextCompat.getColor(this, R.color.correct_answer_background))
                Log.d(TAG, "FullHighlight: ${optionLetter} is CORRECT, set to GREEN")
            } else if (optionLetter.equals(chosenAnswerLetter, ignoreCase = true) && !wasCorrectlyAnswered) {
                radioButton.setBackgroundColor(ContextCompat.getColor(this, R.color.incorrect_answer_background))
                Log.d(TAG, "FullHighlight: ${optionLetter} is CHOSEN & INCORRECT, set to RED")
            } else {
                // Only reset if it's not the correct one (which should remain green)
                if(!optionLetter.equals(correctAnswerLetterFromSubmit, ignoreCase = true)) {
                    radioButton.setBackgroundColor(Color.TRANSPARENT)
                    Log.d(TAG, "FullHighlight: ${optionLetter} is OTHER, set to TRANSPARENT")
                }
            }
        }
    }

    private fun enableRadioButtons(enabled: Boolean) {
        binding.rbOptionA.isEnabled = enabled
        binding.rbOptionB.isEnabled = enabled
        binding.rbOptionC.isEnabled = enabled
        binding.rbOptionD.isEnabled = enabled
    }

    private fun resetOptionColors(excludeCorrect: Boolean = false, correctLetter: String? = null) {
        Log.d(TAG, "resetOptionColors called. excludeCorrect: $excludeCorrect, correctLetter: $correctLetter")
        val defaultColor = Color.TRANSPARENT
        val options = listOf(binding.rbOptionA, binding.rbOptionB, binding.rbOptionC, binding.rbOptionD)
        val optionLetters = listOf("A", "B", "C", "D")

        options.forEachIndexed { index, radioButton ->
            val currentOptionLetter = optionLetters[index]
            if (excludeCorrect && currentOptionLetter.equals(correctLetter, ignoreCase = true)) {
                Log.d(TAG, "resetOptionColors: SKIPPING reset for correct answer $currentOptionLetter")
                // Do nothing, keep its current color (which should be green if already set)
            } else {
                Log.d(TAG, "resetOptionColors: Resetting $currentOptionLetter to TRANSPARENT")
                radioButton.setBackgroundColor(defaultColor)
            }
        }
    }

    private fun showQuizResults() {
        Log.d(TAG, "showQuizResults")
        answerSubmittedForCurrentQuestion = true

        binding.layoutQuizFinished.visibility = View.VISIBLE
        binding.tvFrageNummerCsv.visibility = View.GONE // Hide 'Frage #' on results screen
        binding.tvQuestionProgress.visibility = View.GONE // Hide progress on results screen
        binding.tvQuestionText.visibility = View.GONE
        binding.tvQuestionIndividualStatsTop.visibility = View.GONE

        // ... rest of UI hiding
        val totalQuestions = viewModel.currentQuestions.value?.size ?: 0
        val score = viewModel.score.value ?: 0

        if (totalQuestions == 0) {
            binding.tvQuizResult.text = getString(R.string.no_questions_found)
        } else {
            binding.tvQuizResult.text = getString(R.string.quiz_finished_score_format, score, totalQuestions)
        }
    }
}