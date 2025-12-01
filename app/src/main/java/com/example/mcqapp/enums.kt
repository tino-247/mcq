package com.example.mcqapp

enum class SubCategoryLearningMode {
    ALL,
    UNANSWERED,
    WEAK_RECENT_STREAK,     // New mode representing timesCorrectRecent < 3
    RESET_STATS
}

interface OnSubCategoryActionListener {
    fun onSubCategoryAction(category: String, subCategory: String, mode: SubCategoryLearningMode)
}