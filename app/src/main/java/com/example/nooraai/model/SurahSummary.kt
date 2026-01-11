package com.example.nooraai.model

/**
 * Domain model for a single surah item used across the UI.
 *
 * - number: surah number (1..114)
 * - name: primary display name (prefer localized / id if available)
 * - transliteration: latin/translit name (optional)
 * - translation: translation / short meaning (optional)
 * - ayatCount: number of verses in the surah
 */
data class SurahSummary(
    val number: Int,
    val name: String,
    val transliteration: String? = null,
    val translation: String? = null,
    val ayatCount: Int = 0
)