package com.example.nooraai.model
import com.example.nooraai.data.SurahData

data class SurahDetailResponse(
    val status: Boolean,
    val request: RequestInfo?,
    val info: Info?,
    val data: SurahData?
)

data class Info(
    val min: Int?,
    val max: Int?
)