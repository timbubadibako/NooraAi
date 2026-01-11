package com.example.nooraai.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nooraai.network.RetrofitClient
import com.example.nooraai.data.SurahData
import com.example.nooraai.model.SurahDetailResponse
import com.example.nooraai.model.SurahListResponse
import com.example.nooraai.model.SurahSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel untuk data quran (surah list & detail).
 *
 * Menggunakan RetrofitClient.apiService yang mengembalikan Response<SurahListResponse>
 * dan Response<SurahDetailResponse> seperti di ApiService kamu.
 *
 * Exposes:
 *  - surahList: LiveData<List<SurahSummary>>
 *  - loading: LiveData<Boolean>
 *  - error: LiveData<String?>
 *
 * Provides:
 *  - loadSurahList(force: Boolean = false)
 *  - suspend fetchSurahDetail(nomor: Int): Pair<SurahData?, String?>  (DTO SurahData returned)
 */
class QuranViewModel : ViewModel() {

    private val api = RetrofitClient.apiService

    private val _surahList = MutableLiveData<List<SurahSummary>>(emptyList())
    val surahList: LiveData<List<SurahSummary>> = _surahList

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var loaded = false

    /**
     * Load surah list dari API.
     * Tries /quran/surat/all first, falls back to /quran/surat/semua if necessary.
     */
    fun loadSurahList(force: Boolean = false) {
        if (loaded && !force) return

        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            try {
                // Try preferred endpoint first
                val respAll = withContext(Dispatchers.IO) { api.getAllSuratAll() }
                val response = if (respAll.isSuccessful) respAll else withContext(Dispatchers.IO) { api.getAllSuratSemua() }

                if (response.isSuccessful) {
                    val body: SurahListResponse? = response.body()
                    val dtoList: List<SurahData>? = body?.data
                    val mapped = dtoList?.mapNotNull { dto ->
                        try {
                            mapSurahDataToSummary(dto)
                        } catch (_: Throwable) {
                            null
                        }
                    } ?: emptyList()
                    _surahList.value = mapped
                    loaded = true
                } else {
                    // read error body safely
                    val err = try { response.errorBody()?.string() } catch (_: Exception) { null }
                    _error.value = "Gagal memuat daftar surat: HTTP ${response.code()} ${err ?: ""}".trim()
                }
            } catch (t: Throwable) {
                _error.value = t.localizedMessage ?: "Terjadi kesalahan saat memuat daftar surat"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Fetch surah detail and return Pair(dataDto, errorMessage).
     * dataDto is SurahData (as your SurahDetailResponse.data is SurahData).
     * Returns (null, errorMessage) on failure.
     */
    suspend fun fetchSurahDetail(nomor: Int): Pair<SurahData?, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val resp = api.getSurat(nomor)
                if (resp.isSuccessful) {
                    val body: SurahDetailResponse? = resp.body()
                    val data = body?.data
                    if (data != null) {
                        Pair(data, null)
                    } else {
                        Pair(null, "Response tidak berisi data surah")
                    }
                } else {
                    val err = try { resp.errorBody()?.string() } catch (_: Exception) { null }
                    Pair(null, "HTTP ${resp.code()} ${err ?: ""}".trim())
                }
            } catch (t: Throwable) {
                Pair(null, t.localizedMessage ?: "Terjadi kesalahan jaringan")
            }
        }
    }

    // -------------------------
    // Mapping helper
    // -------------------------
    private fun mapSurahDataToSummary(dto: SurahData): SurahSummary {
        // SurahData fields (as provided in your project):
        // number: String? (API returns number as String)
        // nameId/nameEn/nameLong/nameShort
        // numberOfVerses: String?
        val numberInt = dto.number?.toIntOrNull() ?: 0
        val name = dto.nameId ?: dto.nameEn ?: dto.nameLong ?: "Surah $numberInt"
        val transliteration = dto.nameLong ?: dto.nameShort ?: dto.nameEn ?: ""
        val translation = dto.translationId ?: dto.translationEn ?: ""
        val ayatCount = dto.numberOfVerses?.toIntOrNull() ?: 0

        return SurahSummary(
            number = numberInt,
            name = name,
            transliteration = transliteration,
            translation = translation,
            ayatCount = ayatCount
        )
    }
}