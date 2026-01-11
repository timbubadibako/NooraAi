package com.example.nooraai.util

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.text.Normalizer
import java.util.Locale

object CityMapper {
    private const val PREFS = "noora_prefs"
    private const val KEY_CITIES_JSON = "sholat_cities_v2_json"

    // internal storage: normalizedName -> id, also keep original list (id -> originalName)
    private val normalizedToId = mutableMapOf<String, String>()
    private val idToName = mutableMapOf<String, String>()

    /**
     * Normalize location name for matching:
     * - lowercase
     * - remove common prefixes (kab, kota, kabupaten, kota)
     * - remove punctuation, multiple spaces
     * - remove diacritics (basic)
     */
    private fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var s = input.trim().lowercase(Locale.getDefault())

        // remove common administrative prefixes
        s = s.replace("\\b(kab\\.?|kabupaten|kota|prov\\.?|provinsi|kab|kec\\.?|kecamatan)\\b".toRegex(), " ")

        // remove punctuation and other non-alphanum except space
        s = s.replace("[^a-z0-9\\s]".toRegex(), " ")

        // collapse spaces
        s = s.replace("\\s+".toRegex(), " ").trim()

        // remove diacritics
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")

        return s
    }

    /**
     * Build internal mapping from a JSON string that contains {"data":[ { "id": "...", "lokasi": "..." }, ... ]}
     * Returns number of entries parsed.
     */
    fun buildFromJsonString(json: String): Int {
        normalizedToId.clear()
        idToName.clear()

        try {
            val elem: JsonElement = JsonParser.parseString(json)
            val root = if (elem.isJsonObject) elem.asJsonObject else null
            val dataElem = when {
                root?.has("data") == true -> root.get("data")
                elem.isJsonArray -> elem
                else -> null
            } ?: return 0

            val arr = if (dataElem.isJsonArray) dataElem.asJsonArray else dataElem.asJsonObject.getAsJsonArray("data")
            for (e in arr) {
                try {
                    val o = e.asJsonObject
                    val id = when {
                        o.has("id") -> o.get("id").asString
                        o.has("kota_id") -> o.get("kota_id").asString
                        else -> null
                    } ?: continue
                    val lokasi = when {
                        o.has("lokasi") -> o.get("lokasi").asString
                        o.has("nama") -> o.get("nama").asString
                        o.has("kota") -> o.get("kota").asString
                        else -> ""
                    }

                    val normalized = normalize(lokasi)
                    if (normalized.isNotBlank()) {
                        // if duplicate normalized name, keep first (or replace depending needs)
                        if (!normalizedToId.containsKey(normalized)) {
                            normalizedToId[normalized] = id
                        }
                    }
                    idToName[id] = lokasi
                } catch (_: Exception) {
                    // skip malformed entry
                }
            }
            return normalizedToId.size
        } catch (_: Exception) {
            return 0
        }
    }

    /**
     * Save raw JSON cache to prefs for later reuse
     */
    fun saveJsonToPrefs(context: Context, json: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CITIES_JSON, json).apply()
        } catch (_: Exception) {}
    }

    /**
     * Load mapping from cached prefs JSON if present. Returns true if loaded.
     */
    fun loadFromPrefs(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CITIES_JSON, null) ?: return false
        val count = buildFromJsonString(raw)
        return count > 0
    }

    /**
     * Clear internal cache (in-memory) and optionally from prefs
     */
    fun clearCache(context: Context? = null) {
        normalizedToId.clear()
        idToName.clear()
        context?.let {
            val prefs = it.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_CITIES_JSON).apply()
        }
    }

    /**
     * Find best city id by a user-provided name (e.g., from Geocoder).
     * Returns the id (string) if a confident match found, otherwise null.
     *
     * Matching strategy:
     * - exact normalized match
     * - contains match (normalized key contains query OR query contains key)
     * - token-score fuzzy match (pick best scoring candidate)
     */
    fun findCityIdByName(rawQuery: String?, returnCandidatesIfAmbiguous: Boolean = false): Pair<String?, List<Pair<String, String>>> {
        // returns Pair(bestIdOrNull, candidatesList (id,name) possibly empty)
        val candidates = mutableListOf<Pair<String, String>>()
        if (rawQuery.isNullOrBlank()) return Pair(null, candidates)

        val q = normalize(rawQuery)
        if (q.isBlank()) return Pair(null, candidates)

        // ensure map not empty
        if (normalizedToId.isEmpty()) return Pair(null, candidates)

        // 1) exact
        normalizedToId[q]?.let { id ->
            candidates.add(Pair(id, idToName[id] ?: ""))
            return Pair(id, candidates)
        }

        // 2) contains / reverse contains
        val containsHits = normalizedToId.entries.filter { (norm, _) ->
            norm.contains(q) || q.contains(norm)
        }.map { Pair(it.value, idToName[it.value] ?: "") }

        if (containsHits.size == 1) {
            return Pair(containsHits.first().first, containsHits)
        } else if (containsHits.size > 1) {
            return if (returnCandidatesIfAmbiguous) Pair(null, containsHits) else Pair(containsHits.first().first, containsHits)
        }

        // 3) token-scoring
        val tokens = q.split(" ").filter { it.length >= 2 }
        if (tokens.isNotEmpty()) {
            val scored = normalizedToId.entries.map { (norm, id) ->
                val score = tokens.count { t -> norm.contains(t) }
                Triple(id, idToName[id] ?: "", score)
            }.filter { it.third > 0 }.sortedByDescending { it.third }

            if (scored.isNotEmpty()) {
                val topScore = scored.first().third
                val top = scored.takeWhile { it.third == topScore }
                val candidatesList = top.map { Pair(it.first, it.second) }
                return if (candidatesList.size == 1) Pair(candidatesList.first().first, candidatesList)
                else Pair(null, candidatesList)
            }
        }

        // 4) fallback: fuzzy contains over original names (looser)
        val loose = idToName.entries.filter { (_, name) ->
            val norm = normalize(name)
            norm.contains(q) || q.contains(norm)
        }.map { Pair(it.key, it.value) }

        if (loose.isNotEmpty()) {
            return if (loose.size == 1) Pair(loose.first().first, loose) else Pair(null, loose)
        }

        return Pair(null, emptyList())
    }

    /**
     * Return a short list of candidate pairs (id, original name), limited by `limit`.
     * Useful to show dialog choices to user.
     */
    fun findCandidates(rawQuery: String?, limit: Int = 30): List<Pair<String, String>> {
        val (best, candidates) = findCityIdByName(rawQuery, returnCandidatesIfAmbiguous = true)
        if (candidates.isNotEmpty()) return candidates.take(limit)

        // if empty, try token-based broader search
        if (rawQuery.isNullOrBlank()) return emptyList()
        val q = normalize(rawQuery)
        val tokens = q.split(" ").filter { it.length >= 2 }
        val scored = idToName.entries.map { (id, name) ->
            val norm = normalize(name)
            val score = tokens.count { t -> norm.contains(t) }
            Triple(id, name, score)
        }.filter { it.third > 0 }.sortedByDescending { it.third }.map { Pair(it.first, it.second) }

        return scored.take(limit)
    }
}