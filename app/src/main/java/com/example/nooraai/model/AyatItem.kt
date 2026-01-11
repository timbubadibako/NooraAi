package com.example.nooraai.model

data class AyatItem(
    val ayahNumber: Int,
    val arab: String,
    val latin: String,
    val translation: String,
    val audio: String?  = null
)