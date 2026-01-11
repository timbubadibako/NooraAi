package com.example.nooraai.ui.learn

data class LessonItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val sortOrder: Int? = null,
    val courseId: String? = null
)