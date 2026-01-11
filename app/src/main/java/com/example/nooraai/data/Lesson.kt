package com.example.nooraai.data

/**
 * Simple data model for lessons (maps to public.lessons).
 * Fields kept nullable/defaults so mapping from JSON/API is flexible.
 */
data class Lesson(
    val id: String,
    val course_id: String? = null,
    val slug: String = "",
    val title: String? = null,
    val description: String? = null,
    val content_json: String? = null,         // raw JSON string (or null)
    val sort_order: Int? = null,
    val duration_seconds: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)