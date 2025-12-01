package com.example.mcqapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val subCategory: String,
    val questionNumber: String,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String,
    val imageName: String?,

    var timesAnswered: Int = 0,
    var timesCorrect: Int = 0,
    var timesChosenA: Int = 0,
    var timesChosenB: Int = 0,
    var timesChosenC: Int = 0,
    var timesChosenD: Int = 0,

    var timesCorrectRecent: Int = 0 // Counts consecutive correct answers

) {
    fun getCorrectnessRatio(): Float {
        return if (timesAnswered == 0) 0f else timesCorrect.toFloat() / timesAnswered.toFloat()
    }
}