package com.example.nooraai

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.nooraai.model.AyatItem
import com.example.nooraai.network.RetrofitClient
import com.example.nooraai.ui.detail.AyatAdapter
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

class SurahDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SURAH_NUMBER = "surah_number"
        const val EXTRA_SURAH_NAME = "surah_name"
        const val EXTRA_SURAH_VERSES_COUNT = "surah_verses_count"

        // Quran has 114 surahs
        const val SURAH_MIN = 1
        const val SURAH_MAX = 114
    }

    private val api = RetrofitClient.apiService
    private val TAG = "DetailSurah"

    private lateinit var rvAyahs: RecyclerView
    private lateinit var tvSurahTitle: TextView
    private lateinit var tvSurahInfo: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnBack: ImageView
    private var btnDropdown: ImageView? = null // dropdown to pick surah

    // player UI
    private lateinit var playerTitle: TextView
    private lateinit var playerQari: TextView
    private lateinit var btnPrev: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnSelectQari: ImageView

    private lateinit var ayatAdapter: AyatAdapter

    // audio / playback state
    private var ayatList: List<AyatItem> = emptyList()
    private var currentIndex = 0
    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isPlaying = false

    // session flag (true when bottom-driven continuous playback active)
    private var sessionActive = false

    // modes: SINGLE = only one ayat, SESSION = continuous next
    private enum class PlayMode { NONE, SINGLE, SESSION }
    private var playMode = PlayMode.NONE

    private val PREFS_NAME = "noora_prefs"
    private val KEY_LAST_READ_SURAH_PREFIX = "last_read_surah_"
    private val KEY_BOOKMARKS_JSON = "bookmarks_json" // global bookmarks JSON
    private val KEY_SAVED_AYAH_PREFIX = "saved_ayat_surah_" // optional per-surah saved
    private lateinit var prefs: android.content.SharedPreferences

    // sample qari list (replace with dynamic list if available)
    private val qariList = listOf("Misyari Rasyid", "Abdul Basit", "Mahmoud Khalil", "Saad al-Ghamdi")
    private var selectedQari = qariList.first()

    // current surah number for saving last-read per-surah
    private var currentSurahNumber: Int = SURAH_MIN

    // Debounce timestamp to ignore scroll-based selection when we programmatically change selection/scroll
    private var ignoreNextScrollSelectionUntil = 0L

    // If user clicks a different card while SINGLE is playing, queue that index to play after current single ends
    private var queuedIndexForNextSingle: Int? = null

    // preview index (visual only, grey) used when session is playing to indicate user click
    private var previewIndex: Int = -1

    // pending scroll target (from intent) — when open_full_and_scroll true
    private var pendingScrollToAyah: Int = -1

    private var touchDownX: Float = 0f
    private var touchDownTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surah_detail)

        // If top bar is light request dark status bar icons so time/battery are visible.
        try {
            val topBarColor = ContextCompat.getColor(this, R.color.card_white)
            if (isColorLight(topBarColor)) {
                WindowCompat.getInsetsController(window, window.decorView)
                    ?.isAppearanceLightStatusBars = true
            }
        } catch (_: Exception) { /* ignore */ }

        // find views
        rvAyahs = findViewById(R.id.rvAyahs)
        tvSurahTitle = findViewById(R.id.tvSurahTitle)
        tvSurahInfo = findViewById(R.id.tvSurahInfo)
        progress = findViewById(R.id.progress)
        btnBack = findViewById(R.id.btnBack)
        btnDropdown = findViewById<ImageView?>(R.id.btnDropdown)

        // player views
        playerTitle = findViewById(R.id.player_title)
        playerQari = findViewById(R.id.player_qari)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnSelectQari = findViewById(R.id.btnSelectQari)

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnDropdown?.setOnClickListener { showSurahPicker() }

        // Properly apply system window insets (padding top so header isn't overlapped)
        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight)
            WindowInsetsCompat.CONSUMED
        }

        // prefs + surah id from intent
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentSurahNumber = intent.getIntExtra(EXTRA_SURAH_NUMBER, SURAH_MIN)

        // read last saved ayah for this surah (if any) to initialize adapter state later
        val lastReadKey = KEY_LAST_READ_SURAH_PREFIX + currentSurahNumber
        val lastReadAyah = prefs.getInt(lastReadKey, -1)

        // read extras: support "open_full_and_scroll" with selected_ayat_to as target
        val extraFrom = intent.getIntExtra("selected_ayat_from", -1)
        val extraTo = intent.getIntExtra("selected_ayat_to", -1)
        val openFullAndScroll = intent.getBooleanExtra("open_full_and_scroll", false)
        pendingScrollToAyah = if (openFullAndScroll && extraTo > 0) extraTo else -1
        Log.d(TAG, "onCreate received extras from=$extraFrom to=$extraTo for surah=$currentSurahNumber pendingScrollToAyah=$pendingScrollToAyah")

        // Improved simple swipe (still ACTION_DOWN / ACTION_UP) with vertical tolerance
        val density = resources.displayMetrics.density
        val SWIPE_MIN_DISTANCE_DP = 120f
        val SWIPE_MIN_DISTANCE = (SWIPE_MIN_DISTANCE_DP * density).toInt() // px threshold
        val SWIPE_MAX_TIME = 500 // ms
        val HORIZONTAL_DOMINANCE = 1.5f // horizontal must be 1.5x vertical
        val VERTICAL_MAX_DISTANCE_DP = 80f
        val VERTICAL_MAX_DISTANCE = (VERTICAL_MAX_DISTANCE_DP * density).toInt()

        var touchDownY = 0f
        var moved = false

// Root content: detect deliberate horizontal flings on empty/header area (consume)
        content.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = ev.x
                    touchDownY = ev.y
                    touchDownTime = System.currentTimeMillis()
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // mark that user moved (so small taps not misdetected)
                    val dxMove = Math.abs(ev.x - touchDownX)
                    val dyMove = Math.abs(ev.y - touchDownY)
                    if (dxMove > 8 * density || dyMove > 8 * density) moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - touchDownX
                    val dy = ev.y - touchDownY
                    val dt = System.currentTimeMillis() - touchDownTime

                    val absDx = Math.abs(dx).toInt()
                    val absDy = Math.abs(dy).toInt()

                    val isHorizontalDominant = absDx > absDy * HORIZONTAL_DOMINANCE
                    val isVerticalSmall = absDy <= VERTICAL_MAX_DISTANCE

                    if (dt <= SWIPE_MAX_TIME && absDx >= SWIPE_MIN_DISTANCE && isHorizontalDominant && isVerticalSmall) {
                        if (dx > 0) navigateToNextSurah() else navigateToPreviousSurah()
                        true
                    } else {
                        // not a deliberate horizontal swipe
                        false
                    }
                }
                else -> false
            }
        }

// RecyclerView: do not consume the touch events (so scrolling works), but still listen and trigger navigation if clear horizontal swipe detected.
// Return false so RecyclerView handles scrolling; we only "observe" gesture.
        rvAyahs.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = ev.x
                    touchDownY = ev.y
                    touchDownTime = System.currentTimeMillis()
                    moved = false
                    // return false so RecyclerView will handle the touch as usual
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dxMove = Math.abs(ev.x - touchDownX)
                    val dyMove = Math.abs(ev.y - touchDownY)
                    if (dxMove > 8 * density || dyMove > 8 * density) moved = true
                    // don't consume
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.x - touchDownX
                    val dy = ev.y - touchDownY
                    val dt = System.currentTimeMillis() - touchDownTime

                    val absDx = Math.abs(dx).toInt()
                    val absDy = Math.abs(dy).toInt()

                    val isHorizontalDominant = absDx > absDy * HORIZONTAL_DOMINANCE
                    val isVerticalSmall = absDy <= VERTICAL_MAX_DISTANCE

                    if (dt <= SWIPE_MAX_TIME && absDx >= SWIPE_MIN_DISTANCE && isHorizontalDominant && isVerticalSmall) {
                        // Trigger navigation but DO NOT consume the event: return false so RecyclerView keeps handling it.
                        if (dx > 0) navigateToNextSurah() else navigateToPreviousSurah()
                    }
                    false
                }
                else -> false
            }
        }

        // Initialize adapter with callbacks
        ayatAdapter = AyatAdapter(
            items = emptyList(),
            lastReadAyahNumber = lastReadAyah,
            onPlayClick = { ayat, pos ->
                if (sessionActive && isPlaying) {
                    Toast.makeText(this, "Pause session terlebih dahulu untuk memutar ayat tunggal.", Toast.LENGTH_SHORT).show()
                    return@AyatAdapter
                }
                if (sessionActive && !isPlaying) {
                    stopSession()
                    playSingleAyat(pos)
                    return@AyatAdapter
                }
                if (playMode == PlayMode.SINGLE && isPlaying && currentIndex == pos) {
                    pauseSingle()
                } else if (playMode == PlayMode.SINGLE && isPlaying && currentIndex != pos) {
                    queuedIndexForNextSingle = pos
                    Toast.makeText(this, "Dimasukkan ke antrian: Ayat ${pos + 1}", Toast.LENGTH_SHORT).show()
                } else {
                    ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                    queuedIndexForNextSingle = null
                    playSingleAyat(pos)
                }
            },
            onCardClick = { ayat, pos ->
                ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                if (isPlaying) {
                    previewIndex = pos
                    ayatAdapter.setPreviewIndex(pos)
                } else {
                    saveLastReadAyah(currentSurahNumber, ayat.ayahNumber)
                    ayatAdapter.setLastReadAyah(ayat.ayahNumber)
                    currentIndex = pos
                }
            },
            onSaveClick = { ayat, pos ->
                val added = toggleBookmark(currentSurahNumber, ayat.ayahNumber)
                ayatAdapter.setSavedAyah(if (added) ayat.ayahNumber else -1)
                if (added) Toast.makeText(this, "Ayat ${ayat.ayahNumber} disimpan", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, "Simpan ayat dibatalkan", Toast.LENGTH_SHORT).show()
            }
        )

        rvAyahs.layoutManager = LinearLayoutManager(this)
        rvAyahs.adapter = ayatAdapter
        (rvAyahs.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        attachScrollSaver(currentSurahNumber)

        val passedName = intent.getStringExtra(EXTRA_SURAH_NAME)
        val passedVersesCount = intent.getIntExtra(EXTRA_SURAH_VERSES_COUNT, -1)

        if (!passedName.isNullOrEmpty()) {
            tvSurahTitle.text = passedName
            playerTitle.text = passedName
        }

        if (passedVersesCount > 0) {
            tvSurahInfo.text = "$passedVersesCount Ayat"
        }

        Log.d(TAG, "onCreate extras processed for surah=$currentSurahNumber pendingScrollToAyah=$pendingScrollToAyah")

        btnPlayPause.setOnClickListener {
            if (!sessionActive) {
                val savedAyah = prefs.getInt(KEY_LAST_READ_SURAH_PREFIX + currentSurahNumber, -1)
                val startIdx = if (savedAyah > 0) {
                    ayatList.indexOfFirst { it.ayahNumber == savedAyah }.takeIf { it >= 0 } ?: currentIndex
                } else currentIndex
                previewIndex = -1
                ayatAdapter.setPreviewIndex(-1)
                startSession(startIdx.coerceAtLeast(0))
            } else {
                if (isPlaying) pauseSession() else resumeSession()
            }
        }
        btnNext.setOnClickListener { nextInSession() }
        btnPrev.setOnClickListener { prevInSession() }
        btnSelectQari.setOnClickListener { showQariSelection() }

        // load data
        loadSurahDetail(currentSurahNumber, extraFrom, extraTo)
    }

    // Bookmarks stored as JSON array of objects: { "surah":Int, "ayat":Int, "ts":Long }
    private fun getBookmarks(): MutableList<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        try {
            val raw = prefs.getString(KEY_BOOKMARKS_JSON, null) ?: return list
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val s = obj.optInt("surah", -1)
                val a = obj.optInt("ayat", -1)
                if (s > 0 && a > 0) list.add(Pair(s, a))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveBookmarks(list: List<Pair<Int, Int>>) {
        try {
            val arr = JSONArray()
            for ((s, a) in list) {
                val obj = JSONObject()
                obj.put("surah", s)
                obj.put("ayat", a)
                obj.put("ts", System.currentTimeMillis())
                arr.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS_JSON, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    // toggle bookmark; returns true if added, false if removed
    private fun toggleBookmark(surah: Int, ayah: Int): Boolean {
        val list = getBookmarks()
        val idx = list.indexOfFirst { it.first == surah && it.second == ayah }
        return if (idx >= 0) {
            list.removeAt(idx)
            saveBookmarks(list)
            false
        } else {
            // add at front, limit to 20 entries
            list.add(0, Pair(surah, ayah))
            if (list.size > 20) list.removeAt(list.size - 1)
            saveBookmarks(list)
            true
        }
    }

    private fun findBookmarkForSurah(surah: Int): Int {
        val list = getBookmarks()
        val first = list.firstOrNull { it.first == surah }
        return first?.second ?: -1
    }

    // --- SURAH navigation helpers (dropdown + swipe) ---

    private fun showSurahPicker() {
        val labels = (SURAH_MIN..SURAH_MAX).map { "$it. Surah $it" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih Surat")
            .setItems(labels) { _, which ->
                val chosen = SURAH_MIN + which
                switchToSurah(chosen)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun navigateToNextSurah() {
        val next = if (currentSurahNumber >= SURAH_MAX) SURAH_MIN else currentSurahNumber + 1
        switchToSurah(next)
    }

    private fun navigateToPreviousSurah() {
        val prev = if (currentSurahNumber <= SURAH_MIN) SURAH_MAX else currentSurahNumber - 1
        switchToSurah(prev)
    }

    private fun switchToSurah(surahNumber: Int) {
        if (surahNumber == currentSurahNumber) return
        stopSession()
        releasePlayer()
        currentSurahNumber = surahNumber
        tvSurahTitle.text = "Surah $surahNumber"
        playerTitle.text = "Surah $surahNumber"
        ayatAdapter.updateData(emptyList(), -1)
        // restore bookmark visual for this surah (if any)
        val bk = findBookmarkForSurah(surahNumber)
        ayatAdapter.setSavedAyah(if (bk > 0) bk else -1)
        pendingScrollToAyah = -1
        loadSurahDetail(currentSurahNumber)
    }

    private fun saveLastReadAyah(surahNumber: Int, ayahNumber: Int) {
        try {
            // Try to obtain surah title from UI (fallback to "Surah #N")
            val surahName = try { tvSurahTitle.text?.toString() } catch (_: Exception) { null }
            val nameToSave = if (!surahName.isNullOrBlank()) surahName!! else "Surah #$surahNumber"

            prefs.edit()
                .putInt(KEY_LAST_READ_SURAH_PREFIX + surahNumber, ayahNumber)
                .putInt("last_read_surah_num", surahNumber)
                .putInt("last_read_ayat_num", ayahNumber)
                .putString("last_read_surah_name", nameToSave)
                .apply()
        } catch (_: Exception) {}
    }

    // --- Playback / session methods (full implementations) ---

    private fun playSingleAyat(index: Int) {
        if (index < 0 || index >= ayatList.size) return

        if (sessionActive && isPlaying) {
            Toast.makeText(this, "Pause session dulu untuk memutar ayat tunggal.", Toast.LENGTH_SHORT).show()
            return
        }

        if (sessionActive && !isPlaying) {
            stopSession()
        }

        val ayat = ayatList[index]
        saveLastReadAyah(currentSurahNumber, ayat.ayahNumber)
        ayatAdapter.setLastReadAyah(ayat.ayahNumber)

        playMode = PlayMode.SINGLE
        currentIndex = index
        queuedIndexForNextSingle = null

        val url = ayat.audio
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Audio ayat tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        releasePlayer()

        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener {
                    isPrepared = true
                    start()
                    this@SurahDetailActivity.isPlaying = true
                    ayatAdapter.setPlayingState(currentIndex, true)
                    btnPlayPause.isEnabled = false
                    btnNext.isEnabled = false
                    btnPrev.isEnabled = false
                }
                setOnCompletionListener {
                    this@SurahDetailActivity.isPlaying = false
                    isPrepared = false
                    ayatAdapter.setPlayingState(currentIndex, false)
                    if (queuedIndexForNextSingle != null) {
                        val next = queuedIndexForNextSingle!!
                        queuedIndexForNextSingle = null
                        playSingleAyat(next)
                    } else {
                        btnPlayPause.isEnabled = true
                        btnNext.isEnabled = true
                        btnPrev.isEnabled = true
                        playMode = PlayMode.NONE
                    }
                }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@SurahDetailActivity, "Gagal memutar audio: ${e.message}", Toast.LENGTH_SHORT).show()
                release()
            }
        }
    }

    private fun pauseSingle() {
        if (playMode != PlayMode.SINGLE) return
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                ayatAdapter.setPlayingState(currentIndex, false)
                btnPlayPause.isEnabled = true
                btnNext.isEnabled = true
                btnPrev.isEnabled = true
            }
        }
    }

    private fun startSession(startIndex: Int) {
        if (startIndex < 0 || startIndex >= ayatList.size) return

        if (playMode == PlayMode.SINGLE && isPlaying) {
            releasePlayer()
        }

        sessionActive = true
        playMode = PlayMode.SESSION
        currentIndex = startIndex

        previewIndex = -1
        ayatAdapter.setPreviewIndex(-1)

        ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
        playCurrentAndContinue()
    }

    private fun playCurrentAndContinue() {
        if (!sessionActive) return
        if (currentIndex < 0 || currentIndex >= ayatList.size) {
            stopSession()
            return
        }

        val ayat = ayatList[currentIndex]
        val url = ayat.audio
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Audio ayat tidak tersedia", Toast.LENGTH_SHORT).show()
            nextInSession()
            return
        }

        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener {
                    isPrepared = true
                    start()
                    this@SurahDetailActivity.isPlaying = true
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    btnPlayPause.isEnabled = true
                    btnNext.isEnabled = true
                    btnPrev.isEnabled = true
                    previewIndex = -1
                    ayatAdapter.setPreviewIndex(-1)
                    ayatAdapter.setPlayingState(currentIndex, true)
                }
                setOnCompletionListener {
                    this@SurahDetailActivity.isPlaying = false
                    isPrepared = false
                    ayatAdapter.setPlayingState(currentIndex, false)

                    if (sessionActive) {
                        if (currentIndex < ayatList.size - 1) {
                            currentIndex += 1
                            ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                            rvAyahs.post {
                                (rvAyahs.layoutManager as? LinearLayoutManager)
                                    ?.scrollToPositionWithOffset(currentIndex, 100)
                            }
                            playCurrentAndContinue()
                        } else {
                            stopSession()
                        }
                    } else {
                        stopSession()
                    }
                }
                prepareAsync()
            } catch (e: Exception) {
                Toast.makeText(this@SurahDetailActivity, "Gagal memutar audio: ${e.message}", Toast.LENGTH_SHORT).show()
                release()
            }
        }

        saveLastReadAyah(currentSurahNumber, ayat.ayahNumber)
        ayatAdapter.setLastReadAyah(ayat.ayahNumber)
    }

    private fun pauseSession() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                ayatAdapter.setPlayingState(currentIndex, false)
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun resumeSession() {
        mediaPlayer?.let {
            if (!it.isPlaying && isPrepared) {
                it.start()
                isPlaying = true
                ayatAdapter.setPlayingState(currentIndex, true)
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                playCurrentAndContinue()
            }
        } ?: run {
            playCurrentAndContinue()
        }
    }

    private fun stopSession() {
        sessionActive = false
        playMode = PlayMode.NONE
        queuedIndexForNextSingle = null
        releasePlayer()
    }

    private fun nextInSession() {
        if (!sessionActive) {
            if (currentIndex < ayatList.size - 1) {
                startSession(currentIndex + 1)
            }
            return
        }
        if (currentIndex < ayatList.size - 1) {
            currentIndex += 1
            ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
            rvAyahs.post {
                (rvAyahs.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(currentIndex, 100)
            }
            playCurrentAndContinue()
        } else {
            stopSession()
        }
    }

    private fun prevInSession() {
        if (!sessionActive) {
            if (currentIndex > 0) startSession(currentIndex - 1)
            return
        }
        if (currentIndex > 0) {
            currentIndex -= 1
            ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
            rvAyahs.post {
                (rvAyahs.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(currentIndex, 100)
            }
            playCurrentAndContinue()
        } else {
            mediaPlayer?.seekTo(0)
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            isPrepared = false
            isPlaying = false
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            btnPlayPause.isEnabled = true
            btnNext.isEnabled = true
            btnPrev.isEnabled = true
            ayatAdapter.setPlayingState(-1, false)
        }
    }

    private fun attachScrollSaver(surahNumber: Int) {
        val lm = rvAyahs.layoutManager as? LinearLayoutManager ?: return
        var lastSaved = -1
        rvAyahs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (System.currentTimeMillis() < ignoreNextScrollSelectionUntil) return
                    if (isPlaying || sessionActive) return

                    val pos = lm.findFirstCompletelyVisibleItemPosition().takeIf { it >= 0 }
                        ?: lm.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: -1
                    if (pos >= 0 && pos < ayatList.size) {
                        val ayahNum = ayatList[pos].ayahNumber
                        if (ayahNum != lastSaved) {
                            lastSaved = ayahNum
                            saveLastReadAyah(surahNumber, ayahNum)
                            ayatAdapter.setLastReadAyah(ayahNum)
                            currentIndex = pos
                        }
                    }
                }
            }
        })
    }

    // Place this inside the class SurahDetailActivity

    private fun setLoading(isLoading: Boolean) {
        // runOnUiThread not strictly necessary in coroutine main, but safe
        runOnUiThread {
            try {
                progress.visibility = if (isLoading) View.VISIBLE else View.GONE
            } catch (_: Exception) { }
        }
    }

    private fun showQariSelection() {
        val items = qariList.toTypedArray()
        val checked = qariList.indexOf(selectedQari).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Pilih Qari")
            .setSingleChoiceItems(items, checked) { dialog, which ->
                selectedQari = qariList[which]
                try { playerQari.text = selectedQari } catch (_: Exception) {}
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // --- Loading/parsing functions ---

    private fun loadSurahDetail(number: Int, extraFrom: Int = -1, extraTo: Int = -1) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val resp = api.getSurat(number)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    Log.d(TAG, "Surat detail body: $body")
                    val data = body?.data
                    if (data != null) {
                        val title = data.nameId ?: data.nameEn ?: data.nameLong ?: "Surah ${data.number}"
                        tvSurahTitle.text = title
                        playerTitle.text = title

                        val versesCount = data.numberOfVerses?.toIntOrNull() ?: -1
                        val revelation = data.revelationId ?: data.revelationEn ?: data.revelation ?: ""
                        val infoText = buildString {
                            if (versesCount > 0) append("$versesCount Ayat")
                            if (revelation.isNotEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(revelation)
                            }
                        }
                        if (infoText.isNotEmpty()) tvSurahInfo.text = infoText

                        if (versesCount > 0) {
                            // If caller asked to open full surah and scroll to an ayah, always load full range
                            if (pendingScrollToAyah > 0) {
                                loadAyatRange(number, "1-$versesCount")
                            } else if (extraFrom > 0 && extraTo > 0 && extraTo >= extraFrom) {
                                val s = extraFrom.coerceIn(1, versesCount)
                                val e = extraTo.coerceIn(1, versesCount)
                                loadAyatRange(number, "$s-$e")
                            } else {
                                loadAyatRange(number, "1-$versesCount")
                            }
                        } else {
                            loadAyat(number, 1)
                        }
                    } else {
                        Toast.makeText(this@SurahDetailActivity, "Data surah kosong", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@SurahDetailActivity, "Gagal memuat data: ${resp.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loadSurahDetail: ${e.message}", e)
                Toast.makeText(this@SurahDetailActivity, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadAyatRange(surat: Int, range: String) {
        val parts = range.split("-")
        var start = parts.getOrNull(0)?.toIntOrNull() ?: 1
        var end = parts.getOrNull(1)?.toIntOrNull() ?: start
        if (start < 1) start = 1
        if (end < start) end = start

        if (end - start + 1 > 30) {
            fetchAyatInChunks(surat, start, end)
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val resp = api.getAyatRange(surat, "$start-$end")
                if (!resp.isSuccessful) {
                    Toast.makeText(this@SurahDetailActivity, "Gagal memuat ayat", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val body: JsonObject? = resp.body()
                val dataElem = if (body?.has("data") == true) body.get("data") else null
                val list = parseAyatDataElemToList(dataElem)
                ayatList = list

                val saved = prefs.getInt(KEY_LAST_READ_SURAH_PREFIX + surat, -1)
                ayatAdapter.updateData(list, saved)

                // restore per-surah saved ayah visual (bookmark)
                val savedAyah = findBookmarkForSurah(surat)
                if (savedAyah > 0) ayatAdapter.setSavedAyah(savedAyah) else ayatAdapter.setSavedAyah(-1)

                // If pendingScrollToAyah requested by intent, prioritize that scroll
                if (pendingScrollToAyah > 0) {
                    val idxForPending = list.indexOfFirst { it.ayahNumber == pendingScrollToAyah }
                    if (idxForPending >= 0) {
                        ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                        (rvAyahs.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(idxForPending, 120)
                        currentIndex = idxForPending
                    }
                    pendingScrollToAyah = -1
                } else if (saved > 0) {
                    val idx = list.indexOfFirst { it.ayahNumber == saved }
                    if (idx >= 0) {
                        ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                        (rvAyahs.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(idx, 120)
                        currentIndex = idx
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loadAyatRange: ${e.message}", e)
                Toast.makeText(this@SurahDetailActivity, "Terjadi kesalahan saat memuat ayat", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadAyat(surat: Int, ayat: Int) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val resp = api.getAyat(surat, ayat)
                if (!resp.isSuccessful) {
                    Toast.makeText(this@SurahDetailActivity, "Gagal memuat ayat", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val body: JsonObject? = resp.body()
                val dataElem = if (body?.has("data") == true) body.get("data") else null
                val list = parseAyatDataElemToList(dataElem)
                ayatList = list
                val saved = prefs.getInt(KEY_LAST_READ_SURAH_PREFIX + surat, -1)
                ayatAdapter.updateData(list, saved)
                val savedAyah = findBookmarkForSurah(surat)
                if (savedAyah > 0) ayatAdapter.setSavedAyah(savedAyah) else ayatAdapter.setSavedAyah(-1)

                // if pendingScrollToAyah set and matches this small list, scroll
                if (pendingScrollToAyah > 0) {
                    val idxForPending = list.indexOfFirst { it.ayahNumber == pendingScrollToAyah }
                    if (idxForPending >= 0) {
                        ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                        (rvAyahs.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(idxForPending, 120)
                        currentIndex = idxForPending
                    }
                    pendingScrollToAyah = -1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loadAyat: ${e.message}", e)
                Toast.makeText(this@SurahDetailActivity, "Terjadi kesalahan saat memuat ayat", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun fetchAyatInChunks(surat: Int, startInclusive: Int, endInclusive: Int) {
        lifecycleScope.launch {
            setLoading(true)
            val combined = mutableListOf<AyatItem>()
            try {
                var s = startInclusive
                while (s <= endInclusive) {
                    val chunkEnd = min(s + 29, endInclusive)
                    val rangeStr = "$s-$chunkEnd"
                    val resp = api.getAyatRange(surat, rangeStr)
                    if (!resp.isSuccessful) {
                        Toast.makeText(this@SurahDetailActivity, "Gagal memuat ayat ($rangeStr)", Toast.LENGTH_SHORT).show()
                        break
                    }
                    val body: JsonObject? = resp.body()
                    val dataElem = if (body?.has("data") == true) body.get("data") else null
                    val chunkList = parseAyatDataElemToList(dataElem)
                    combined.addAll(chunkList)
                    s = chunkEnd + 1
                }
                ayatList = combined
                val saved = prefs.getInt(KEY_LAST_READ_SURAH_PREFIX + surat, -1)
                ayatAdapter.updateData(combined, saved)
                val savedAyah = findBookmarkForSurah(surat)
                if (savedAyah > 0) ayatAdapter.setSavedAyah(savedAyah) else ayatAdapter.setSavedAyah(-1)

                // handle pending scroll if requested
                if (pendingScrollToAyah > 0) {
                    val idxForPending = combined.indexOfFirst { it.ayahNumber == pendingScrollToAyah }
                    if (idxForPending >= 0) {
                        ignoreNextScrollSelectionUntil = System.currentTimeMillis() + 600L
                        (rvAyahs.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(idxForPending, 120)
                        currentIndex = idxForPending
                    }
                    pendingScrollToAyah = -1
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetchAyatInChunks: ${e.message}", e)
                Toast.makeText(this@SurahDetailActivity, "Terjadi kesalahan saat memuat ayat", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun parseAyatDataElemToList(dataElem: JsonElement?): List<AyatItem> {
        val list = mutableListOf<AyatItem>()
        if (dataElem == null) return list

        val versesArr: JsonArray? = when {
            dataElem.isJsonArray -> dataElem.asJsonArray
            dataElem.isJsonObject -> {
                val dobj = dataElem.asJsonObject
                when {
                    dobj.has("verses") && dobj.get("verses").isJsonArray -> dobj.getAsJsonArray("verses")
                    dobj.has("ayahs") && dobj.get("ayahs").isJsonArray -> dobj.getAsJsonArray("ayahs")
                    else -> null
                }
            }
            else -> null
        }

        if (versesArr != null) {
            for (v in versesArr) {
                val obj = v.asJsonObject

                val ayahNum = when {
                    obj.has("ayah") -> obj.get("ayah").asString.toIntOrNull() ?: 0
                    obj.has("number") -> obj.get("number").asInt
                    else -> 0
                }

                val arab = when {
                    obj.has("arab") -> obj.get("arab").asString
                    obj.has("text_uthmani") -> obj.get("text_uthmani").asString
                    else -> ""
                }

                val latin = if (obj.has("latin")) obj.get("latin").asString else ""
                val translation = if (obj.has("text")) obj.get("text").asString else ""
                val audio = if (obj.has("audio")) obj.get("audio").asString else null

                list.add(AyatItem(ayahNum, arab, latin, translation, audio))
            }
        }

        return list
    }

    // utility: color luminance check
    private fun isColorLight(color: Int): Boolean {
        val r = (color shr 16 and 0xff) / 255.0
        val g = (color shr 8 and 0xff) / 255.0
        val b = (color and 0xff) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return luminance > 0.6
    }
}