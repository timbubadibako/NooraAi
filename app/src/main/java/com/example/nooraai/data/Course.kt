package com.example.nooraai.data
// Course.kt
data class Course(
    val id: String,
    val slug: String,
    val title: String,
    val description: String?,
    val lessons: Int = 0,
    val leftColor: String? = null,
    val rightColor: String? = null
)