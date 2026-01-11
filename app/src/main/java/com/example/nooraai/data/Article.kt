package com.example.nooraai.data

/**
 * Maps to public.articles
 */
data class Article(
    val id: String,
    val lesson_part_id: String? = null,
    val title: String,
    val excerpt: String? = null,
    val content: String? = null,
    val content_json: String? = null,
    val language: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)