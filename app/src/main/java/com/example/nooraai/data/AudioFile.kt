package com.example.nooraai.data

/**
 * Maps to public.audio_files
 */
data class AudioFile(
    val id: String,
    val article_id: String? = null,
    val lesson_id: String? = null,
    val type: String? = null,
    val storage_path: String,    // URL or storage path
    val filename: String? = null,
    val mime_type: String? = null,
    val duration_seconds: Int? = null,
    val metadata: String? = null,
    val created_at: String? = null
)