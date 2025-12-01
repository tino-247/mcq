package com.example.mcqapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mcqapp.OnSubCategoryActionListener
import com.example.mcqapp.databinding.ListItemCategoryHeaderBinding
import com.example.mcqapp.databinding.ListItemSubcategoryStatisticBinding
import com.example.mcqapp.ui.models.StatisticListItem
import java.text.NumberFormat
import com.example.mcqapp.SubCategoryLearningMode
import com.example.mcqapp.databinding.ListItemAllQuestionsSummaryBinding

// Add listener as a constructor parameter
class StatisticsAdapter(private val subCategoryActionListener: OnSubCategoryActionListener) :
    ListAdapter<StatisticListItem, RecyclerView.ViewHolder>(StatisticDiffCallback()) {

    companion object {
        private const val TYPE_ALL_QUESTIONS_SUMMARY = 0 // New
        private const val TYPE_CATEGORY_HEADER = 1
        private const val TYPE_SUBCATEGORY_STAT = 2
    }

    private val percentFormat = NumberFormat.getPercentInstance().apply {
        maximumFractionDigits = 0
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is StatisticListItem.AllQuestionsSummary -> TYPE_ALL_QUESTIONS_SUMMARY // New
            is StatisticListItem.CategoryHeader -> TYPE_CATEGORY_HEADER
            is StatisticListItem.SubCategoryStatistic -> TYPE_SUBCATEGORY_STAT
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ALL_QUESTIONS_SUMMARY -> { // New
                val binding = ListItemAllQuestionsSummaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AllQuestionsSummaryViewHolder(binding, subCategoryActionListener) // Pass listener
            }
            TYPE_CATEGORY_HEADER -> {
                val binding = ListItemCategoryHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                CategoryHeaderViewHolder(binding)
            }
            TYPE_SUBCATEGORY_STAT -> {
                val binding = ListItemSubcategoryStatisticBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SubCategoryStatViewHolder(binding, percentFormat, subCategoryActionListener)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AllQuestionsSummaryViewHolder -> holder.bind(getItem(position) as StatisticListItem.AllQuestionsSummary) // New
            is CategoryHeaderViewHolder -> holder.bind(getItem(position) as StatisticListItem.CategoryHeader)
            is SubCategoryStatViewHolder -> holder.bind(getItem(position) as StatisticListItem.SubCategoryStatistic)
        }
    }

    class AllQuestionsSummaryViewHolder(
        private val binding: ListItemAllQuestionsSummaryBinding,
        private val listener: OnSubCategoryActionListener // To handle button clicks
    ) : RecyclerView.ViewHolder(binding.root) {
        private val percentFormat = NumberFormat.getPercentInstance().apply {
            maximumFractionDigits = 0
        }
        fun bind(item: StatisticListItem.AllQuestionsSummary) {
            binding.tvSummaryTotalQuestions.text = "Total Questions in DB: ${item.totalQuestions}"
            binding.tvSummaryDistinctAnswered.text = "Distinct Questions Answered: ${item.totalDistinctQuestionsAnswered} / ${item.totalQuestions}"
            binding.tvSummaryTotalAttempts.text = "Total Attempts Made: ${item.totalAttempts}"
            binding.tvSummaryAvgCorrectness.text = "Overall Avg. Correctness: ${percentFormat.format(item.overallAverageCorrectness)}"

            // Click listeners for overall buttons
            // Category and SubCategory will be null for these "overall" actions
            binding.btnLearnAllOverall.setOnClickListener {
                listener.onSubCategoryAction(category = "_OVERALL_", subCategory = "_ALL_", mode = SubCategoryLearningMode.ALL)
            }
            binding.btnLearnUnansweredOverall.setOnClickListener {
                listener.onSubCategoryAction(category = "_OVERALL_", subCategory = "_UNANSWERED_", mode = SubCategoryLearningMode.UNANSWERED)
            }
            binding.btnLearnWeakOverall.setOnClickListener {
                listener.onSubCategoryAction(category = "_OVERALL_", subCategory = "_WEAK_", mode = SubCategoryLearningMode.WEAK_RECENT_STREAK)
            }
        }
    }


    class CategoryHeaderViewHolder(private val binding: ListItemCategoryHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        // ... (same as before)
        fun bind(item: StatisticListItem.CategoryHeader) {
            binding.tvCategoryHeader.text = item.categoryName
        }
    }

    class SubCategoryStatViewHolder(
        private val binding: ListItemSubcategoryStatisticBinding,
        private val percentFormat: NumberFormat,
        private val listener: OnSubCategoryActionListener // Use new listener type
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: StatisticListItem.SubCategoryStatistic? = null

        init {
            binding.btnLearnAllSubCategory.setOnClickListener {
                currentItem?.let {
                    listener.onSubCategoryAction(it.parentCategoryName, it.subCategoryName, SubCategoryLearningMode.ALL)
                }
            }
            binding.btnLearnUnansweredSubCategory.setOnClickListener {
                currentItem?.let {
                    listener.onSubCategoryAction(it.parentCategoryName, it.subCategoryName, SubCategoryLearningMode.UNANSWERED)
                }
            }
            // Updated for the "Weak" button
            binding.btnLearnWeakSubCategory.setOnClickListener { // ID changed
                currentItem?.let {
                    // Pass the new mode
                    listener.onSubCategoryAction(it.parentCategoryName, it.subCategoryName, SubCategoryLearningMode.WEAK_RECENT_STREAK)
                }
            }
            binding.btnResetStatsSubCategory.setOnClickListener {
                currentItem?.let {
                    listener.onSubCategoryAction(it.parentCategoryName, it.subCategoryName, SubCategoryLearningMode.RESET_STATS)
                }
            }
        }


        fun bind(item: StatisticListItem.SubCategoryStatistic) {
            this.currentItem = item
            binding.tvSubCategoryName.text = item.subCategoryName
            binding.tvSubCategoryTotalQuestions.text = "Total Questions: ${item.totalQuestions}"
            binding.tvSubCategoryTotalAnswered.text = "Questions Answered: ${item.totalAnswered} / ${item.totalQuestions}"
            binding.tvSubCategoryAvgCorrectness.text = "Avg. Correctness (all attempts): ${percentFormat.format(item.averageCorrectness)}"
            binding.tvSubCategoryAvgStreak.text = "Avg. Recent Streak: %.1f".format(item.averageRecentStreak)
        }
    }


    class StatisticDiffCallback : DiffUtil.ItemCallback<StatisticListItem>() {
        override fun areItemsTheSame(oldItem: StatisticListItem, newItem: StatisticListItem): Boolean {
            return when {
                oldItem is StatisticListItem.AllQuestionsSummary && newItem is StatisticListItem.AllQuestionsSummary -> true // Only one summary row
                oldItem is StatisticListItem.CategoryHeader && newItem is StatisticListItem.CategoryHeader ->
                    oldItem.categoryName == newItem.categoryName
                oldItem is StatisticListItem.SubCategoryStatistic && newItem is StatisticListItem.SubCategoryStatistic ->
                    oldItem.parentCategoryName == newItem.parentCategoryName && oldItem.subCategoryName == newItem.subCategoryName
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: StatisticListItem, newItem: StatisticListItem): Boolean {
            return oldItem == newItem
        }
    }

}