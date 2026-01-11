package com.example.nooraai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nooraai.databinding.ActivityTadarusBinding
import com.example.nooraai.model.SurahSummary
import com.example.nooraai.ui.home.SurahListAdapter
import com.example.nooraai.ui.compass.KiblatoverlayFragment
import com.example.nooraai.util.LocationHelper
import com.example.nooraai.viewmodel.QuranViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TadarusActivity : BaseActivity() {

    private lateinit var binding: ActivityTadarusBinding
    private lateinit var viewModel: QuranViewModel
    private lateinit var surahAdapter: SurahListAdapter
    private lateinit var prefs: SharedPreferences

    private val POPULAR_SURAH_IDS = listOf(1, 18, 36, 55, 56, 67)

    private var selectedSurah: SurahSummary? = null
    private var selectedAyatRangeText: String = "Ayat 1-7"

    // key for bookmarks json
    private val KEY_BOOKMARKS_JSON = "bookmarks_json"

    override fun getLayoutId(): Int = R.layout.activity_tadarus
    override fun getNavIndex(): Int = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView)?.isAppearanceLightStatusBars = true

        val childRoot = getChildRootView()
        binding = ActivityTadarusBinding.bind(childRoot)

        val ivCompass = findViewById<ImageView?>(R.id.ivCompass)
        ivCompass?.setOnClickListener {
            KiblatoverlayFragment().show(supportFragmentManager, "kiblat")
        }

        val ivBellHeader = binding.root.findViewById<ImageView?>(R.id.ivBell)
        ivBellHeader?.setOnClickListener {
            com.example.nooraai.ui.prayer.PrayerTimesOverlayFragment().show(supportFragmentManager, "prayer_overlay")
        }

        // location chip
        val tvLocationText = binding.root.findViewById<TextView>(R.id.tvLocationText)
        if (tvLocationText != null) {
            LocationHelper.fetchAndFillLocation(this, tvLocationText) { lat, lon -> }
            val locationChip = binding.root.findViewById<View>(R.id.location_chip)
            locationChip?.setOnClickListener {
                LocationHelper.fetchAndFillLocation(this, tvLocationText) { lat, lon ->
                    Toast.makeText(this, "Lokasi diperbarui", Toast.LENGTH_SHORT).show()
                }
            }
        }

        prefs = getSharedPreferences("noora_prefs", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this).get(QuranViewModel::class.java)

        surahAdapter = SurahListAdapter(
            onClick = { surah ->
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra(SurahDetailActivity.EXTRA_SURAH_NUMBER, surah.number)
                    putExtra(SurahDetailActivity.EXTRA_SURAH_NAME, surah.name ?: surah.name)
                    putExtra(SurahDetailActivity.EXTRA_SURAH_VERSES_COUNT, surah.ayatCount)
                }
                startActivity(intent)
            },
            onPlayClick = { surah ->
                Toast.makeText(this, "Play audio: ${surah.number}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSurahList.layoutManager = LinearLayoutManager(this)
        binding.rvSurahList.adapter = surahAdapter
        binding.rvSurahList.isNestedScrollingEnabled = false

        // Views for last-read (left) and continue (right)
        val cardLastRead = binding.root.findViewById<View>(R.id.cardLastRead)
        val tvLastSurahName = binding.root.findViewById<TextView>(R.id.tvLastSurahName)
        val tvLastSurahAyat = binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)

        val cardContinue = binding.root.findViewById<View>(R.id.cardContinue)
        val tvContinueSurahName = binding.root.findViewById<TextView>(R.id.tvContinueSurahName)
        val tvContinueSurahAyat = binding.root.findViewById<TextView>(R.id.tvContinueSurahAyat)

        viewModel.surahList.observe(this) { list ->
            val mapByNumber = list.associateBy { it.number }
            val display = POPULAR_SURAH_IDS.mapNotNull { id -> mapByNumber[id] }
            surahAdapter.submitList(display)

            if (selectedSurah == null) {
                if (display.isNotEmpty()) {
                    setSelectedSurah(display.first())
                } else {
                    binding.tvSelectSurah.text = "Belum ada"
                    safeSetEditText(binding.etAyatFrom, "")
                    safeSetEditText(binding.etAyatTo, "")
                }
            }

            // PRIORITIZE bookmark: show most recent bookmark if exists
            val bm = getMostRecentBookmark()
            if (bm != null) {
                val (bmSurah, bmAyah) = bm
                val surahObj = list.firstOrNull { it.number == bmSurah }
                tvLastSurahName.text = surahObj?.name ?: "Surah #$bmSurah"
                tvLastSurahAyat.text = "Ayat $bmAyah"
            } else {
                // fallback to last-read if no bookmarks
                val lastSurahNum = prefs.getInt("last_read_surah_num", -1)
                val lastAyat = prefs.getInt("last_read_ayat_num", -1)
                if (lastSurahNum > 0) {
                    val s = list.firstOrNull { it.number == lastSurahNum }
                    tvLastSurahName.text = s?.name ?: "Surah #$lastSurahNum"
                    tvLastSurahAyat.text = if (lastAyat > 0) "Ayat $lastAyat" else "Belum ada"
                } else {
                    tvLastSurahName.text = "Belum ada penanda surat"
                    tvLastSurahAyat.text = "Belum ada"
                }
            }

            // RIGHT CARD: show last-read (continue) — unchanged
            val lastSurahNum = prefs.getInt("last_read_surah_num", -1)
            val lastAyat = prefs.getInt("last_read_ayat_num", -1)
            if (lastSurahNum > 0) {
                val s = list.firstOrNull { it.number == lastSurahNum }
                tvContinueSurahName.text = s?.name ?: "Surah #$lastSurahNum"
                tvContinueSurahAyat.text = if (lastAyat > 0) "Ayat $lastAyat" else "Belum ada"
            } else {
                tvContinueSurahName.text = "Belum ada bacaan terakhir"
                tvContinueSurahAyat.text = "Belum ada"
            }
        }

        viewModel.error.observe(this) { err ->
            err?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        viewModel.loadSurahList()

        binding.btnTrackStart.setOnClickListener {
            startActivity(Intent(this, LearnActivity::class.java))
        }

        // initialize left card fallback (most recent bookmark)
        run {
            val bm = getMostRecentBookmark()
            if (bm != null) {
                binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = "Surah #${bm.first}"
                binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text = "Ayat ${bm.second}"
            } else {
                val lastSurahNum = prefs.getInt("last_read_surah_num", -1)
                val lastAyat = prefs.getInt("last_read_ayat_num", -1)
                if (lastSurahNum > 0) {
                    binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = "Surah #$lastSurahNum"
                    binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text =
                        if (lastAyat > 0) "Ayat $lastAyat" else "Belum ada"
                } else {
                    binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = "Belum ada penanda surat"
                    binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text = "Belum ada"
                }
            }
        }

        // Click left card: open SurahDetail for the bookmarked single ayah (if exists),
        // otherwise open last-read.
        cardLastRead.setOnClickListener {
            val bm = getMostRecentBookmark()
            if (bm != null) {
                val (surah, ayah) = bm
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra(SurahDetailActivity.EXTRA_SURAH_NUMBER, surah)
                    putExtra("selected_ayat_from", ayah)
                    putExtra("selected_ayat_to", ayah)
                }
                startActivity(intent)
                return@setOnClickListener
            }

            // fallback to last-read
            val lastSur = prefs.getInt("last_read_surah_num", -1)
            val lastAy = prefs.getInt("last_read_ayat_num", -1)
            if (lastSur > 0) {
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra(SurahDetailActivity.EXTRA_SURAH_NUMBER, lastSur)
                    if (lastAy > 0) {
                        putExtra("selected_ayat_from", lastAy)
                        putExtra("selected_ayat_to", lastAy)
                    }
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Belum ada penanda surat atau bacaan terakhir.", Toast.LENGTH_SHORT).show()
            }
        }

        // Click right card -> open last-read surah and scroll to last-read ayah (full surah)
        cardContinue.setOnClickListener {
            val lastSur = prefs.getInt("last_read_surah_num", -1)
            val lastAy = prefs.getInt("last_read_ayat_num", -1)
            if (lastSur > 0) {
                val intent = Intent(this, SurahDetailActivity::class.java).apply {
                    putExtra(SurahDetailActivity.EXTRA_SURAH_NUMBER, lastSur)
                    putExtra("open_full_and_scroll", true)
                    if (lastAy > 0) putExtra("selected_ayat_to", lastAy)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Belum ada bacaan terakhir.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.selectSurah.setOnClickListener {
            val all = viewModel.surahList.value
            if (all.isNullOrEmpty()) {
                Toast.makeText(this, "Daftar surat belum dimuat.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val names = all.map { it.name ?: it.name ?: "Surah ${it.number}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            AlertDialog.Builder(this)
                .setTitle("Pilih Surat")
                .setAdapter(adapter) { _, which ->
                    val picked = all[which]
                    setSelectedSurah(picked)
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        binding.selectAyat.setOnClickListener {
            binding.etAyatFrom?.requestFocus()
        }

        binding.btnSearchSurah.setOnClickListener {
            // ... same as before ...
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarHeight)
            WindowInsetsCompat.CONSUMED
        }
    }

    // Return most recent bookmark (surah, ayah) or null
    private fun getMostRecentBookmark(): Pair<Int, Int>? {
        try {
            val raw = prefs.getString(KEY_BOOKMARKS_JSON, null) ?: return null
            val arr = JSONArray(raw)
            if (arr.length() == 0) return null
            val obj = arr.optJSONObject(0) ?: return null
            val s = obj.optInt("surah", -1)
            val a = obj.optInt("ayat", -1)
            return if (s > 0 && a > 0) Pair(s, a) else null
        } catch (_: Exception) {
            return null
        }
    }

    override fun onResume() {
        super.onResume()

        // Prefer bookmarks first
        val bm = getMostRecentBookmark()
        if (bm != null) {
            val (bmSurah, bmAyah) = bm
            val surah = viewModel.surahList.value?.firstOrNull { it.number == bmSurah }
            binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = surah?.name ?: "Surah #$bmSurah"
            binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text = "Ayat $bmAyah"
        } else {
            // fallback to last-read (prefer stored name if present)
            val lastSurahNum = prefs.getInt("last_read_surah_num", -1)
            val lastAyat = prefs.getInt("last_read_ayat_num", -1)
            val lastName = prefs.getString("last_read_surah_name", null)
            if (lastSurahNum > 0) {
                val name = lastName ?: viewModel.surahList.value?.firstOrNull { it.number == lastSurahNum }?.name ?: "Surah #$lastSurahNum"
                binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = name
                binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text = if (lastAyat > 0) "Ayat $lastAyat" else "Belum ada"
            } else {
                binding.root.findViewById<TextView>(R.id.tvLastSurahName)?.text = "Belum ada penanda surat"
                binding.root.findViewById<TextView>(R.id.tvLastSurahAyat)?.text = "Belum ada"
            }
        }

        // refresh continue card (last-read)
        val lastSur = prefs.getInt("last_read_surah_num", -1)
        val lastAy = prefs.getInt("last_read_ayat_num", -1)
        val lastNamePref = prefs.getString("last_read_surah_name", null)
        if (lastSur > 0) {
            val name = lastNamePref ?: viewModel.surahList.value?.firstOrNull { it.number == lastSur }?.name ?: "Surah #$lastSur"
            binding.root.findViewById<TextView>(R.id.tvContinueSurahName)?.text = name
            binding.root.findViewById<TextView>(R.id.tvContinueSurahAyat)?.text = if (lastAy > 0) "Ayat $lastAy" else "Belum ada"
        } else {
            binding.root.findViewById<TextView>(R.id.tvContinueSurahName)?.text = "Belum ada bacaan terakhir"
            binding.root.findViewById<TextView>(R.id.tvContinueSurahAyat)?.text = "Belum ada"
        }
    }

    private fun setSelectedSurah(surah: SurahSummary) {
        selectedSurah = surah
        binding.tvSelectSurah.text = surah.name ?: surah.name ?: "Surah ${surah.number}"
        val max = surah.ayatCount.coerceAtLeast(1)
        safeSetEditText(binding.etAyatFrom, "1")
        safeSetEditText(binding.etAyatTo, "$max")
        selectedAyatRangeText = "Ayat 1-$max"
    }

    private fun safeSetEditText(et: EditText?, value: String) {
        try { et?.setText(value) } catch (_: Exception) {}
    }

    // convenience format extension for display
    private fun Double.format(decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", this)
}