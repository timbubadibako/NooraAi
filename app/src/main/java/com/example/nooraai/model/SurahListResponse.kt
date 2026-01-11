package com.example.nooraai.model
import com.example.nooraai.data.SurahData

data class SurahListResponse(
    val status: Boolean,
    val request: RequestInfo?,
    val data: List<SurahData>?
)

data class RequestInfo(
    val path: String?
)