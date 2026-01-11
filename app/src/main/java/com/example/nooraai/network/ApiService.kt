package com.example.nooraai.network

import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import com.example.nooraai.model.SurahListResponse
import com.example.nooraai.model.SurahDetailResponse

interface ApiService {

    // --- Prayer endpoints (myquran) ---
    // Preferred single-string date format: yyyy-MM-dd
    @GET("sholat/jadwal/{kota}/{date}")
    suspend fun getPrayerSchedule(
        @Path("kota") kota: String,
        @Path("date") date: String // e.g. "2024-06-23"
    ): Response<JsonObject>

    // Alternative format using year/month/day parts
    @GET("sholat/jadwal/{kota}/{year}/{month}/{day}")
    suspend fun getPrayerScheduleByParts(
        @Path("kota") kota: String,
        @Path("year") year: Int,
        @Path("month") month: Int,
        @Path("day") day: Int
    ): Response<JsonObject>

    // NEW: fetch all kota (v2 list you provided)
    @GET("sholat/kota/semua")
    suspend fun getAllSholatKota(): Response<JsonObject>

    // --- Quran endpoints (existing) ---
    @GET("quran/surat/semua")
    suspend fun getAllSuratSemua(): Response<SurahListResponse>

    @GET("quran/surat/all")
    suspend fun getAllSuratAll(): Response<SurahListResponse>

    @GET("quran/surat/{nomor}")
    suspend fun getSurat(
        @Path("nomor") nomor: Int
    ): Response<SurahDetailResponse>

    @GET("quran/ayat/{surat}/{ayat}")
    suspend fun getAyat(
        @Path("surat") surat: Int,
        @Path("ayat") ayat: Int
    ): Response<JsonObject>

    @GET("quran/ayat/{surat}/{range}")
    suspend fun getAyatRange(
        @Path("surat") surat: Int,
        @Path(value = "range", encoded = true) range: String
    ): Response<JsonObject>

    @GET("quran/ayat/acak")
    suspend fun getAyatAcak(): Response<JsonObject>

    @GET("quran/ayat/random")
    suspend fun getAyatRandom(): Response<JsonObject>

    @GET("quran/surat/semua")
    fun getAllSuratSemuaCall(): Call<JsonObject>
}