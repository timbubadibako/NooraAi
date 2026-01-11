package com.example.nooraai.model

/**
 * Model untuk jadwal sholat yang dipakai oleh PrayerRepository dan adapter.
 */
data class PrayerItem(
    val id: String,          // key, mis. "subuh"
    val name: String,        // display name, mis. "Subuh"
    val hour: Int,
    val minute: Int,
    val isNext: Boolean = false,
    val alarmEnabled: Boolean = false
)