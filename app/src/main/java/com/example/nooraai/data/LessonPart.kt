package com.example.nooraai.data

/**
 * Maps to public.lesson_parts
 */
data class LessonPart(
    val id: String,
    val lesson_id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val content_json: String? = null,
    val sort_order: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)