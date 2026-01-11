package com.example.nooraai.data

import com.google.gson.annotations.SerializedName

data class SurahData(
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("name_en") val nameEn: String?,
    @SerializedName("name_id") val nameId: String?,
    @SerializedName("name_long") val nameLong: String?,
    @SerializedName("name_short") val nameShort: String?,
    @SerializedName("number") val number: String?, // API returns number as String
    @SerializedName("number_of_verses") val numberOfVerses: String?,
    val revelation: String?,
    @SerializedName("revelation_en") val revelationEn: String?,
    @SerializedName("revelation_id") val revelationId: String?,
    val sequence: String?,
    val tafsir: String?,
    @SerializedName("translation_en") val translationEn: String?,
    @SerializedName("translation_id") val translationId: String?
)