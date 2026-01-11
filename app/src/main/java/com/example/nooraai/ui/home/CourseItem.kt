package com.example.nooraai.ui.home

data class CourseItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val lessons: Int? = 0,        // article_count
    val leftColor: String? = null,
    val rightColor: String? = null,
    val level: Int? = null        // from metadata.level
)