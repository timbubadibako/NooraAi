package com.example.nooraai.data

import android.util.Log
import com.example.nooraai.BuildConfig
import com.example.nooraai.ui.home.CourseItem
import com.example.nooraai.ui.learn.LessonItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * ContentRepository (data layer)
 *
 * - Responsible for fetching categories and courses from Supabase REST.
 * - Returns Result<List<Category>> and Result<List<CourseItem>>.
 * - Not an Adapter — keep UI code separate.
 *
 * Improvements:
 * - Parses metadata.level into CourseItem.level
 * - Preserves left_color/right_color and article_count
 * - Adds logging for mapped results
 */
class ContentRepository {

    private val TAG = "ContentRepository"
    private val gson = Gson()
    private val client = OkHttpClient()

    private val baseUrlRaw: String = BuildConfig.SUPABASE_URL
    private val apiKey: String = BuildConfig.SUPABASE_ANON_KEY

    private fun normalizedBaseUrl(): String {
        // Normalize SUPABASE_URL to ensure it contains "/rest/v1/" and ends with a slash.
        var b = baseUrlRaw.trim()
        if (b.isEmpty()) return b

        // remove trailing slashes for a moment
        while (b.endsWith("/")) b = b.dropLast(1)

        return when {
            b.endsWith("/rest/v1") -> "$b/"
            b.contains("/rest/v1") -> if (b.endsWith("/")) b else "$b/"
            else -> "$b/rest/v1/"
        }
    }

    /**
     * Fetch categories.
     */
    suspend fun getCategories(activeOnly: Boolean = true): Result<List<Category>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getCategories: rawBase='$baseUrlRaw' normalized='${normalizedBaseUrl()}' apiKeyPresent=${apiKey.isNotBlank()} apiKeyLen=${apiKey.length}")

            if (apiKey.isBlank()) {
                Log.e(TAG, "Supabase ANON key is blank. Set BuildConfig.SUPABASE_ANON_KEY")
                return@withContext Result.failure(Exception("Supabase ANON key not set"))
            }

            val base = normalizedBaseUrl()
            if (base.isBlank()) {
                Log.e(TAG, "Normalized SUPABASE_URL is blank. raw='$baseUrlRaw'")
                return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))
            }

            val httpUrl = (base + "categories").toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

            val builder = httpUrl.newBuilder()
                .addQueryParameter("select", "id,slug,name,description,is_active")
                .addQueryParameter("order", "id.asc")

            if (activeOnly) {
                builder.addQueryParameter("is_active", "eq.true")
            }

            val url = builder.build().toString()

            Log.d(TAG, "getCategories - requesting url=$url ; apikey present=${apiKey.isNotBlank()} activeOnly=$activeOnly")

            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .build()

            try {
                Log.d(TAG, "getCategories - Request headers:\n${req.headers}")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to log request headers", t)
            }

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string() ?: ""
                Log.d(TAG, "getCategories HTTP $code - url=$url")
                Log.d(TAG, "getCategories response snippet: ${bodyStr.take(800)}")

                if (!resp.isSuccessful) {
                    val msg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "getCategories failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = try {
                    gson.fromJson(bodyStr, mapType) ?: emptyList()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed parsing categories JSON: $bodyStr", t)
                    emptyList()
                }

                val mapped = rawList.mapNotNull { m ->
                    try {
                        val id = when (val idv = m["id"]) {
                            is Number -> idv.toInt()
                            is String -> idv.toIntOrNull()
                            else -> null
                        } ?: return@mapNotNull null

                        val slug = (m["slug"] as? String) ?: ""
                        val name = (m["name"] as? String) ?: ""
                        val description = (m["description"] as? String)
                        val isActive = (m["is_active"] as? Boolean) ?: true
                        Category(id = id, slug = slug, name = name, description = description, isActive = isActive)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Skipping category item due to parse error: $m", t)
                        null
                    }
                }

                return@withContext Result.success(mapped)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception getCategories: ${t.message}\n${t.stackTraceToString()}")
            return@withContext Result.failure(t)
        }
    }

    /**
     * Fetch courses for a given category id.
     * Parses metadata.level into CourseItem.level, preserves left_color/right_color and article_count.
     */
    suspend fun getCoursesForCategory(categoryId: Int): Result<List<CourseItem>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getCoursesForCategory: rawBase='$baseUrlRaw' normalized='${normalizedBaseUrl()}' apiKeyPresent=${apiKey.isNotBlank()} apiKeyLen=${apiKey.length}")

            if (apiKey.isBlank()) {
                Log.e(TAG, "Supabase ANON key is blank. Set BuildConfig.SUPABASE_ANON_KEY")
                return@withContext Result.failure(Exception("Supabase ANON key not set"))
            }

            val base = normalizedBaseUrl()
            if (base.isBlank()) {
                Log.e(TAG, "Normalized SUPABASE_URL is blank. raw='$baseUrlRaw'")
                return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))
            }

            val httpUrl = (base + "courses").toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

            val url = httpUrl.newBuilder()
                .addQueryParameter(
                    "select",
                    "id,slug,title,description,metadata,left_color,right_color,article_count,sort_order,category_id"
                )
                .addQueryParameter("category_id", "eq.$categoryId")
                .addQueryParameter("order", "sort_order.asc")
                .build()
                .toString()

            Log.d(TAG, "getCoursesForCategory - requesting url=$url ; apikey present=${apiKey.isNotBlank()}")

            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .build()

            try {
                Log.d(TAG, "getCoursesForCategory - Request headers:\n${req.headers}")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to log request headers", t)
            }

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string() ?: ""
                Log.d(TAG, "getCoursesForCategory($categoryId) HTTP $code - url=$url")
                Log.d(TAG, "getCoursesForCategory response snippet: ${bodyStr.take(1000)}")

                if (!resp.isSuccessful) {
                    val msg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "getCoursesForCategory failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = try {
                    gson.fromJson(bodyStr, mapType) ?: emptyList()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed parsing courses JSON: $bodyStr", t)
                    emptyList()
                }

                val mapped = rawList.mapNotNull { m ->
                    try {
                        // id is uuid string in DB
                        val id = when (val idv = m["id"]) {
                            is String -> idv
                            is Number -> idv.toString()
                            else -> ""
                        }

                        val title = (m["title"] as? String) ?: ""
                        val description = (m["description"] as? String)
                        val leftColor = (m["left_color"] as? String)
                        val rightColor = (m["right_color"] as? String)
                        val lessons = when (val v = m["article_count"]) {
                            is Number -> v.toInt()
                            is String -> v.toIntOrNull() ?: 0
                            else -> 0
                        }

                        // parse metadata.level if present (metadata is most likely a Map)
                        var level: Int? = null
                        try {
                            val metaObj = m["metadata"]
                            if (metaObj is Map<*, *>) {
                                val lv = metaObj["level"]
                                level = when (lv) {
                                    is Number -> lv.toInt()
                                    is String -> lv.toIntOrNull()
                                    else -> null
                                }
                            }
                        } catch (_: Throwable) {
                            level = null
                        }

                        CourseItem(
                            id = id,
                            title = title,
                            subtitle = description,
                            lessons = lessons,
                            leftColor = leftColor,
                            rightColor = rightColor,
                            level = level
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Skipping course item due to parse error: $m", t)
                        null
                    }
                }

                Log.d(TAG, "getCoursesForCategory mapped count=${mapped.size} titles=${mapped.map { it.title }}")
                return@withContext Result.success(mapped)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception getCoursesForCategory: ${t.message}\n${t.stackTraceToString()}")
            return@withContext Result.failure(t)
        }
    }

    suspend fun getLessonsForCourse(courseId: String, accessToken: String? = null): Result<List<LessonItem>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "getLessonsForCourse: courseId=$courseId accessTokenProvided=${!accessToken.isNullOrBlank()}")

                if (courseId.isBlank()) {
                    return@withContext Result.success(emptyList())
                }

                if (apiKey.isBlank()) {
                    Log.e(TAG, "Supabase ANON key is blank. Set BuildConfig.SUPABASE_ANON_KEY")
                    return@withContext Result.failure(Exception("Supabase ANON key not set"))
                }

                val base = normalizedBaseUrl()
                if (base.isBlank()) {
                    Log.e(TAG, "Normalized SUPABASE_URL is blank. raw='$baseUrlRaw'")
                    return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))
                }

                val httpUrl = (base + "lessons").toHttpUrlOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

                // select fields and filter by course_id (assumes course_id type matches courseId format)
                val url = httpUrl.newBuilder()
                    .addQueryParameter("select", "id,title,description,sort_order,course_id")
                    .addQueryParameter("course_id", "eq.$courseId")
                    .addQueryParameter("order", "sort_order.asc")
                    .build()
                    .toString()

                val reqBuilder = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey", apiKey)
                    .addHeader("Accept", "application/json")

                if (!accessToken.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $accessToken")
                }

                val req = reqBuilder.build()
                try { Log.d(TAG, "getLessonsForCourse - Request headers:\n${req.headers}") } catch (_: Throwable) {}

                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val bodyStr = resp.body?.string() ?: ""
                    Log.d(TAG, "getLessonsForCourse($courseId) HTTP $code - url=$url")
                    Log.d(TAG, "getLessonsForCourse response snippet: ${bodyStr.take(1000)}")

                    if (!resp.isSuccessful) {
                        val msg = "HTTP $code: $bodyStr"
                        Log.e(TAG, "getLessonsForCourse failed: $msg")
                        return@withContext Result.failure(Exception(msg))
                    }

                    val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                    val rawList: List<Map<String, Any>> = try {
                        gson.fromJson(bodyStr, mapType) ?: emptyList()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed parsing lessons JSON: $bodyStr", t)
                        emptyList()
                    }

                    val mapped = rawList.mapNotNull { m ->
                        try {
                            val id = when (val idv = m["id"]) {
                                is String -> idv
                                is Number -> idv.toString()
                                else -> ""
                            }
                            val title = (m["title"] as? String) ?: ""
                            val description = (m["description"] as? String)
                            val sortOrder = when (val v = m["sort_order"]) {
                                is Number -> v.toInt()
                                is String -> v.toIntOrNull()
                                else -> null
                            }
                            val cId = (m["course_id"] as? String) ?: (m["course_id"]?.toString())

                            LessonItem(
                                id = id,
                                title = title,
                                description = description,
                                sortOrder = sortOrder,
                                courseId = cId
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "Skipping lesson item due to parse error: $m", t)
                            null
                        }
                    }

                    Log.d(TAG, "getLessonsForCourse mapped count=${mapped.size} titles=${mapped.map { it.title }}")
                    return@withContext Result.success(mapped)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception getLessonsForCourse: ${t.message}\n${t.stackTraceToString()}")
                return@withContext Result.failure(t)
            }
        }

    // Fetch a single Lesson (may return null if not found)
    suspend fun getLessonById(lessonId: String): Result<com.example.nooraai.data.Lesson?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getLessonById: lessonId=$lessonId")

            if (lessonId.isBlank()) return@withContext Result.success(null)
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("Supabase ANON key not set"))

            val base = normalizedBaseUrl()
            if (base.isBlank()) return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))

            val httpUrl = (base + "lessons").toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

            val url = httpUrl.newBuilder()
                // request the "content" column (jsonb) — not content_json
                .addQueryParameter("select", "id,course_id,slug,title,description,content,sort_order,duration_seconds,created_at,updated_at")
                .addQueryParameter("id", "eq.$lessonId")
                .build()
                .toString()

            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Accept", "application/json")
                .build()

            try { Log.d(TAG, "getLessonById - Request headers:\n${req.headers}") } catch (_: Throwable) {}

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string() ?: ""
                Log.d(TAG, "getLessonById($lessonId) HTTP $code")
                Log.d(TAG, "getLessonById response snippet: ${bodyStr.take(800)}")

                if (!resp.isSuccessful) {
                    val msg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "getLessonById failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = try {
                    gson.fromJson(bodyStr, mapType) ?: emptyList()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed parsing lesson JSON: $bodyStr", t)
                    emptyList()
                }

                val first = rawList.firstOrNull()?.let { m ->
                    try {
                        val id = when (val idv = m["id"]) {
                            is String -> idv
                            is Number -> idv.toString()
                            else -> ""
                        }
                        val courseId = (m["course_id"] as? String) ?: (m["course_id"]?.toString())
                        val slug = (m["slug"] as? String) ?: ""
                        val title = (m["title"] as? String) ?: ""
                        val description = (m["description"] as? String)

                        // content column is likely a Map or List (jsonb). Normalize to JSON string.
                        val contentJson: String? = try {
                            when (val c = m["content"]) {
                                is String -> c
                                null -> null
                                else -> gson.toJson(c)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed converting lesson.content to JSON string", t)
                            null
                        }

                        val sortOrder = when (val v = m["sort_order"]) {
                            is Number -> v.toInt()
                            is String -> v.toIntOrNull()
                            else -> null
                        }
                        val duration = when (val v = m["duration_seconds"]) {
                            is Number -> v.toInt()
                            is String -> v.toIntOrNull()
                            else -> null
                        }
                        val createdAt = (m["created_at"] as? String)
                        val updatedAt = (m["updated_at"] as? String)

                        com.example.nooraai.data.Lesson(
                            id = id,
                            course_id = courseId,
                            slug = slug,
                            title = title,
                            description = description,
                            content_json = contentJson,
                            sort_order = sortOrder,
                            duration_seconds = duration,
                            created_at = createdAt,
                            updated_at = updatedAt
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed mapping lesson item: $m", t)
                        null
                    }
                }

                return@withContext Result.success(first)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception getLessonById: ${t.message}\n${t.stackTraceToString()}")
            return@withContext Result.failure(t)
        }
    }

    // Fetch lesson_parts for a given lessonId
    // ContentRepository.kt — ganti fungsi getLessonParts dengan versi sederhana ini
    suspend fun getLessonParts(lessonId: String, accessToken: String? = null): Result<List<com.example.nooraai.data.LessonPart>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "getLessonParts (simple fetch): lessonId=$lessonId accessTokenProvided=${!accessToken.isNullOrBlank()}")

                if (lessonId.isBlank()) return@withContext Result.success(emptyList())
                if (apiKey.isBlank()) return@withContext Result.failure(Exception("Supabase ANON key not set"))

                val base = normalizedBaseUrl()
                if (base.isBlank()) return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))

                val httpUrl = (base + "lesson_parts").toHttpUrlOrNull()
                    ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

                // Simple request: don't add lesson_id filter on server — fetch rows and filter client-side
                val url = httpUrl.newBuilder()
                    .addQueryParameter("select", "id,lesson_id,title,description,content,content_json,sort_order,created_at,updated_at")
                    .addQueryParameter("order", "sort_order.asc")
                    .build()
                    .toString()

                val reqBuilder = okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey", apiKey)
                    .addHeader("Accept", "application/json")

                if (!accessToken.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $accessToken")
                }

                val req = reqBuilder.build()
                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val bodyStr = resp.body?.string() ?: ""
                    Log.d(TAG, "getLessonParts(simple) HTTP $code")
                    Log.d(TAG, "getLessonParts(simple) response snippet: ${bodyStr.take(2000)}")

                    if (!resp.isSuccessful) {
                        val msg = "HTTP $code: $bodyStr"
                        Log.e(TAG, "getLessonParts(simple) failed: $msg")
                        return@withContext Result.failure(Exception(msg))
                    }

                    val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                    val rawList: List<Map<String, Any>> = try {
                        gson.fromJson(bodyStr, mapType) ?: emptyList()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed parsing lesson_parts JSON: $bodyStr", t)
                        emptyList()
                    }

                    // Filter client-side by lesson_id
                    val filtered = rawList.filter { m ->
                        val lid = (m["lesson_id"] as? String) ?: (m["lesson_id"]?.toString())
                        lid == lessonId
                    }

                    val mapped = filtered.mapNotNull { m ->
                        try {
                            val id = when (val idv = m["id"]) {
                                is String -> idv
                                is Number -> idv.toString()
                                else -> ""
                            }
                            val lessonIdVal = (m["lesson_id"] as? String) ?: (m["lesson_id"]?.toString())
                            val title = (m["title"] as? String)
                            val description = (m["description"] as? String)
                            val contentJson = when (val cj = m["content_json"] ?: m["content"]) {
                                is String -> cj
                                null -> null
                                else -> gson.toJson(cj)
                            }
                            val sortOrder = when (val v = m["sort_order"]) {
                                is Number -> v.toInt()
                                is String -> v.toIntOrNull()
                                else -> null
                            }
                            val createdAt = (m["created_at"] as? String)
                            val updatedAt = (m["updated_at"] as? String)

                            com.example.nooraai.data.LessonPart(
                                id = id,
                                lesson_id = lessonIdVal,
                                title = title,
                                description = description,
                                content_json = contentJson,
                                sort_order = sortOrder,
                                created_at = createdAt,
                                updated_at = updatedAt
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "Skipping lesson_part item due to parse error: $m", t)
                            null
                        }
                    }

                    Log.d(TAG, "getLessonParts(simple) mapped count=${mapped.size} (after client filter)")
                    return@withContext Result.success(mapped)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception getLessonParts(simple): ${t.message}\n${t.stackTraceToString()}")
                return@withContext Result.failure(t)
            }
        }

    suspend fun getArticlesForPart(lessonPartId: String): Result<List<Article>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getArticlesForPart: lessonPartId=$lessonPartId")

            if (lessonPartId.isBlank()) return@withContext Result.success(emptyList())
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("Supabase ANON key not set"))

            val base = normalizedBaseUrl()
            if (base.isBlank()) return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))

            val httpUrl = (base + "articles").toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

            val url = httpUrl.newBuilder()
                .addQueryParameter("select", "id,lesson_part_id,title,excerpt,content,content_json,language,created_at,updated_at")
                .addQueryParameter("lesson_part_id", "eq.$lessonPartId")
                .addQueryParameter("order", "created_at.asc")
                .build()
                .toString()

            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Accept", "application/json")
                .build()

            try { Log.d(TAG, "getArticlesForPart - Request headers:\n${req.headers}") } catch (_: Throwable) {}

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string() ?: ""
                Log.d(TAG, "getArticlesForPart($lessonPartId) HTTP $code")
                Log.d(TAG, "getArticlesForPart response snippet: ${bodyStr.take(1000)}")

                if (!resp.isSuccessful) {
                    val msg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "getArticlesForPart failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = try {
                    gson.fromJson(bodyStr, mapType) ?: emptyList()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed parsing articles JSON: $bodyStr", t)
                    emptyList()
                }

                val mapped = rawList.mapNotNull { m ->
                    try {
                        val id = when (val idv = m["id"]) {
                            is String -> idv
                            is Number -> idv.toString()
                            else -> ""
                        }
                        val lpId = (m["lesson_part_id"] as? String) ?: (m["lesson_part_id"]?.toString())
                        val title = (m["title"] as? String) ?: ""
                        val excerpt = (m["excerpt"] as? String)
                        val content = (m["content"] as? String)
                        val contentJson = when (val cj = m["content_json"]) {
                            is String -> cj
                            else -> gson.toJson(cj) // try to preserve JSON as string
                        }
                        val language = (m["language"] as? String)

                        Article(
                            id = id,
                            lesson_part_id = lpId,
                            title = title,
                            excerpt = excerpt,
                            content = content,
                            content_json = contentJson,
                            language = language,
                            created_at = (m["created_at"] as? String),
                            updated_at = (m["updated_at"] as? String)
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Skipping article due to parse error: $m", t)
                        null
                    }
                }

                return@withContext Result.success(mapped)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception getArticlesForPart: ${t.message}\n${t.stackTraceToString()}")
            return@withContext Result.failure(t)
        }
    }

    suspend fun getAudioFilesForArticle(articleId: String): Result<List<AudioFile>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "getAudioFilesForArticle: articleId=$articleId")

            if (articleId.isBlank()) return@withContext Result.success(emptyList())
            if (apiKey.isBlank()) return@withContext Result.failure(Exception("Supabase ANON key not set"))

            val base = normalizedBaseUrl()
            if (base.isBlank()) return@withContext Result.failure(Exception("Invalid SUPABASE_URL: $baseUrlRaw"))

            val httpUrl = (base + "audio_files").toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("Invalid SUPABASE_URL after normalization: $base"))

            val url = httpUrl.newBuilder()
                .addQueryParameter("select", "id,article_id,lesson_id,type,storage_path,filename,mime_type,duration_seconds,metadata,created_at")
                .addQueryParameter("article_id", "eq.$articleId")
                .addQueryParameter("order", "created_at.asc")
                .build()
                .toString()

            val req = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", apiKey)
                .addHeader("Accept", "application/json")
                .build()

            try { Log.d(TAG, "getAudioFilesForArticle - Request headers:\n${req.headers}") } catch (_: Throwable) {}

            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val bodyStr = resp.body?.string() ?: ""
                Log.d(TAG, "getAudioFilesForArticle($articleId) HTTP $code")
                Log.d(TAG, "getAudioFilesForArticle response snippet: ${bodyStr.take(1000)}")

                if (!resp.isSuccessful) {
                    val msg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "getAudioFilesForArticle failed: $msg")
                    return@withContext Result.failure(Exception(msg))
                }

                val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawList: List<Map<String, Any>> = try {
                    gson.fromJson(bodyStr, mapType) ?: emptyList()
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed parsing audio_files JSON: $bodyStr", t)
                    emptyList()
                }

                val mapped = rawList.mapNotNull { m ->
                    try {
                        val id = when (val idv = m["id"]) {
                            is String -> idv
                            is Number -> idv.toString()
                            else -> ""
                        }
                        val aId = (m["article_id"] as? String) ?: (m["article_id"]?.toString())
                        val lId = (m["lesson_id"] as? String) ?: (m["lesson_id"]?.toString())
                        val type = (m["type"] as? String)
                        val storagePath = (m["storage_path"] as? String) ?: (m["storage_path"]?.toString())
                        val filename = (m["filename"] as? String)
                        val mimeType = (m["mime_type"] as? String)
                        val duration = when (val v = m["duration_seconds"]) {
                            is Number -> v.toInt()
                            is String -> v.toIntOrNull()
                            else -> null
                        }
                        val metadataStr = try { gson.toJson(m["metadata"]) } catch (_: Throwable) { null }

                        AudioFile(
                            id = id,
                            article_id = aId,
                            lesson_id = lId,
                            type = type,
                            storage_path = storagePath ?: "",
                            filename = filename,
                            mime_type = mimeType,
                            duration_seconds = duration,
                            metadata = metadataStr,
                            created_at = (m["created_at"] as? String)
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Skipping audio_file due to parse error: $m", t)
                        null
                    }
                }

                return@withContext Result.success(mapped)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exception getAudioFilesForArticle: ${t.message}\n${t.stackTraceToString()}")
            return@withContext Result.failure(t)
        }
    }
}