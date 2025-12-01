package com.example.mcqapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log // For logging
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog // For confirmation
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mcqapp.OnSubCategoryActionListener
import com.example.mcqapp.SubCategoryLearningMode
import com.example.mcqapp.databinding.ActivityStatisticsBinding

class StatisticsActivity : AppCompatActivity(), OnSubCategoryActionListener {

    private lateinit var binding: ActivityStatisticsBinding
    private val statisticsViewModel: StatisticsViewModel by viewModels()
    private val quizViewModel: QuizViewModel by viewModels() // For starting quizzes
    private lateinit var statisticsAdapter: StatisticsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarStatistics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()

        statisticsViewModel.groupedStatistics.observe(this) { groupedItems ->
            Log.d("StatisticsActivity", "Observed grouped statistics update.") // Add log
            groupedItems?.let {
                statisticsAdapter.submitList(it)
            }
        }
    }

    private fun setupRecyclerView() {
        statisticsAdapter = StatisticsAdapter(this)
        binding.rvStatistics.apply {
            adapter = statisticsAdapter
            layoutManager = LinearLayoutManager(this@StatisticsActivity)
        }
    }

    override fun onSubCategoryAction(category: String, subCategory: String, mode: SubCategoryLearningMode) {
        val isOverallAction = category == "_OVERALL_" // Check for our special marker

        if (isOverallAction) {
            // Handle overall actions
            val intent = Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_LEARNING_MODE, mode.name) // Pass mode
                // Category and SubCategory are null for overall
                putExtra(QuizActivity.EXTRA_CATEGORY, null as String?)
                putExtra(QuizActivity.EXTRA_SUB_CATEGORY, null as String?)
                // onlyWeak will be determined by QuizActivity based on mode
                putExtra(QuizActivity.EXTRA_ONLY_WEAK, mode == SubCategoryLearningMode.WEAK_RECENT_STREAK)
            }
            startActivity(intent)
        } else if (mode == SubCategoryLearningMode.RESET_STATS) {
            showResetConfirmationDialog(category, subCategory)
        } else {
            // Handle specific sub-category actions (existing logic)
            val intent = Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_CATEGORY, category)
                putExtra(QuizActivity.EXTRA_SUB_CATEGORY, subCategory)
                putExtra(QuizActivity.EXTRA_LEARNING_MODE, mode.name)
                putExtra(QuizActivity.EXTRA_ONLY_WEAK, mode == SubCategoryLearningMode.WEAK_RECENT_STREAK)
            }
            startActivity(intent)
        }
    }

    private fun showResetConfirmationDialog(category: String, subCategory: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Reset Statistics")
            .setMessage("Are you sure you want to reset all answering statistics for sub-category '$subCategory' in '$category'? This action cannot be undone for these specific questions.")
            .setPositiveButton("Reset") { _, _ ->
                statisticsViewModel.resetStatisticsFor(category, subCategory)
                Toast.makeText(this, "Statistics for $subCategory reset.", Toast.LENGTH_SHORT).show()
                // The LiveData in StatisticsViewModel should update the list automatically
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}