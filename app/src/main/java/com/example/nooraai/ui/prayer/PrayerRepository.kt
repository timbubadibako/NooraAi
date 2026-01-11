package com.example.nooraai.ui.prayer

import android.util.Log
import com.example.nooraai.network.RetrofitClient
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.example.nooraai.model.PrayerItem

object PrayerRepository {
    private const val TAG = "PrayerRepository"

    private val ORDER = listOf(
        "imsak" to "Imsak",
        "subuh" to "Subuh",
        "terbit" to "Terbit",
        "dzuhur" to "Dzuhur",
        "ashar" to "Ashar",
        "maghrib" to "Maghrib",
        "isya" to "Isya"
    )

    // support H:mm, HH:mm and optionally seconds H:mm:ss
    private val formats = listOf(
        DateTimeFormatter.ofPattern("H:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm")
    )

    /**
     * Fetch prayer times for a given cityId and date (dateStr = "yyyy-MM-dd").
     * This function is resilient to slight differences in JSON structure returned by the API.
     */
    suspend fun fetchForDate(cityId: String, dateStr: String): List<PrayerItem> = withContext(Dispatchers.IO) {
        try {
            val resp = RetrofitClient.apiService.getPrayerSchedule(cityId, dateStr)
            if (!resp.isSuccessful) {
                Log.w(TAG, "getPrayerSchedule unsuccessful: ${resp.code()} ${resp.message()}")
                return@withContext emptyList()
            }

            val body: JsonObject? = resp.body()
            if (body == null) {
                Log.w(TAG, "getPrayerSchedule: body is null")
                return@withContext emptyList()
            }

            // Try to locate the object containing times. Common shapes:
            // 1) data.jadwal.data -> object with keys imsak, subuh, ...
            // 2) data -> object that directly contains time keys
            // 3) body.data could be an array with single object containing jadwal/data
            val jadwalObj = findJadwalObject(body) ?: run {
                Log.w(TAG, "getPrayerSchedule: jadwal object not found in response")
                return@withContext emptyList()
            }

            val items = mutableListOf<PrayerItem>()
            for ((key, label) in ORDER) {
                var timeStr: String? = tryGetString(jadwalObj, key)

                // some APIs use alternate key for dzuhur
                if (timeStr.isNullOrBlank() && key == "dzuhur") {
                    timeStr = tryGetString(jadwalObj, "dhuhr")
                }

                // skip if not present
                if (timeStr.isNullOrBlank()) continue

                val parsed = parseTime(timeStr)
                if (parsed != null) {
                    items.add(PrayerItem(key, label, parsed.hour, parsed.minute))
                } else {
                    Log.w(TAG, "Unable to parse time for key=$key value='$timeStr'")
                }
            }

            // determine next prayer relative to now
            val now = LocalTime.now()
            val nextIdx = items.indexOfFirst { LocalTime.of(it.hour, it.minute).isAfter(now) }
            val chosenIdx = if (nextIdx >= 0) nextIdx else if (items.isNotEmpty()) 0 else -1

            return@withContext items.mapIndexed { i, it -> it.copy(isNext = (i == chosenIdx)) }
        } catch (e: Exception) {
            Log.e(TAG, "fetchForDate error", e)
            return@withContext emptyList()
        }
    }

    // try a few strategies to find the JSON object that holds time keys
    private fun findJadwalObject(body: JsonObject): JsonObject? {
        try {
            // 1) body.data.jadwal.data
            if (body.has("data")) {
                val dataElem = body.get("data")
                if (dataElem.isJsonObject) {
                    val dataObj = dataElem.asJsonObject
                    // check data.jadwal.data
                    if (dataObj.has("jadwal")) {
                        val jadwalElem = dataObj.get("jadwal")
                        if (jadwalElem.isJsonObject) {
                            val jadwalObj = jadwalElem.asJsonObject
                            if (jadwalObj.has("data") && jadwalObj.get("data").isJsonObject) {
                                return jadwalObj.getAsJsonObject("data")
                            }
                            // sometimes jadwal itself contains schedules
                            return jadwalObj
                        }
                    }
                    // sometimes times are directly under data
                    if (looksLikeTimeObject(dataObj)) return dataObj
                } else if (dataElem.isJsonArray) {
                    // e.g., data: [ { jadwal: { data: {...} } } ]
                    val arr = dataElem.asJsonArray
                    if (arr.size() > 0) {
                        val first = arr[0]
                        if (first.isJsonObject) {
                            val fo = first.asJsonObject
                            if (fo.has("jadwal") && fo.get("jadwal").isJsonObject) {
                                val jadwal = fo.getAsJsonObject("jadwal")
                                if (jadwal.has("data") && jadwal.get("data").isJsonObject) {
                                    return jadwal.getAsJsonObject("data")
                                } else return jadwal
                            }
                            if (looksLikeTimeObject(fo)) return fo
                        }
                    }
                }
            }

            // 2) body may directly contain time keys
            if (looksLikeTimeObject(body)) return body

        } catch (_: Exception) {
            // ignore and return null below
        }
        return null
    }

    // heuristics: check presence of at least a couple known keys
    private fun looksLikeTimeObject(obj: JsonObject): Boolean {
        if (obj.has("subuh") || obj.has("imsak") || obj.has("maghrib") || obj.has("isya")) return true
        // also accept common english/different keys
        if (obj.has("dhuhr") || obj.has("sunrise") || obj.has("imsak")) return true
        return false
    }

    // safe get string helper
    private fun tryGetString(obj: JsonObject, key: String): String? {
        return try {
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.getAsJsonPrimitive(key).asString.trim() else null
        } catch (_: Exception) {
            null
        }
    }

    // parse times tolerant to "H:mm", "HH:mm", "H:mm:ss" or "HH:mm:ss"
    private fun parseTime(s: String): LocalTime? {
        val cleaned = s.trim()
        for (fmt in formats) {
            try {
                return LocalTime.parse(cleaned, fmt)
            } catch (_: Exception) {
                // try next
            }
        }
        // try to remove seconds if present like "05:12:00" -> "05:12"
        val parts = cleaned.split(":")
        if (parts.size >= 2) {
            try {
                val hh = parts[0].toInt()
                val mm = parts[1].toInt()
                return LocalTime.of(hh, mm)
            } catch (_: Exception) {}
        }
        return null
    }
}