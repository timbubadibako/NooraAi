package com.example.nooraai.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.LearnActivity
import com.example.nooraai.R
import com.example.nooraai.SurahDetailActivity
import com.example.nooraai.data.Category
import com.example.nooraai.data.ContentRepository
import com.example.nooraai.model.SurahSummary
import com.example.nooraai.viewmodel.QuranViewModel
import android.content.res.ColorStateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var rvCourses: RecyclerView
    private lateinit var adapter: CourseAdapter

    // category container (dynamic)
    private lateinit var categoryContainer: LinearLayout
    private lateinit var categoryScroll: View // HorizontalScrollView (typed as View to avoid import)
    private var selectedCategoryId: Int? = null

    // quiz views (from included layout_quiz_card)
    private lateinit var btnQuizAudio: ImageButton
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var btnOption4: Button
    private lateinit var tvQuizQuestion: TextView

    // star/heart views
    private lateinit var imgQuizStar: ImageView
    private lateinit var imgQuizHeart: ImageView
    private lateinit var tvQuizStarCount: TextView
    private lateinit var tvQuizHeartCount: TextView

    // Surah list (new)
    private lateinit var rvSurahList: RecyclerView
    private lateinit var surahAdapter: SurahListAdapter
    private lateinit var quranViewModel: QuranViewModel

    private val POPULAR_SURAH_IDS = listOf(1, 18, 36, 55, 56, 67)

    private val fallbackData = mapOf(
        "Dasar" to listOf(
            CourseItem(id = "", title = "Dasar: Pengenalan Huruf", subtitle = "Level 1", lessons = 3),
            CourseItem(id = "", title = "Dasar: Bacaan Sederhana", subtitle = "Level 2", lessons = 4)
        ),
        "Tajwid" to listOf(
            CourseItem(id = "", title = "Tajwid: Hukum Nun Sakinah", subtitle = "Level 1", lessons = 5),
            CourseItem(id = "", title = "Tajwid: Madd", subtitle = "Level 2", lessons = 4)
        ),
        "Hafalan" to listOf(
            CourseItem(id = "", title = "Surah Pilihan: Al-Mulk", subtitle = "Level 1", lessons = 4),
            CourseItem(id = "", title = "Surah Pilihan: Yasin", subtitle = "Level 2", lessons = 6)
        )
    )

    private var currentCategory = "Hafalan"
    private val correctAnswer = "Ikhfa"
    private var starCount = 0
    private var heartCount = 1

    private val contentRepo = ContentRepository()
    private var categoriesList: List<Category> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // bind views
        rvCourses = view.findViewById(R.id.rvCourses)
        categoryContainer = view.findViewById(R.id.categoryContainer)
        categoryScroll = view.findViewById(R.id.categoryScroll)

        btnQuizAudio = view.findViewById(R.id.btnQuizAudio)
        btnOption1 = view.findViewById(R.id.btnOption1)
        btnOption2 = view.findViewById(R.id.btnOption2)
        btnOption3 = view.findViewById(R.id.btnOption3)
        btnOption4 = view.findViewById(R.id.btnOption4)
        tvQuizQuestion = view.findViewById(R.id.tvQuizQuestion)

        imgQuizStar = view.findViewById(R.id.imgQuizStar)
        imgQuizHeart = view.findViewById(R.id.imgQuizHeart)
        tvQuizStarCount = view.findViewById(R.id.tvQuizStarCount)
        tvQuizHeartCount = view.findViewById(R.id.tvQuizHeartCount)

        rvSurahList = view.findViewById(R.id.rvSurahList)

        adapter = CourseAdapter { courseItem ->
            val ctx = requireContext()
            val i = Intent(ctx, LearnActivity::class.java).apply {
                putExtra("category_id", selectedCategoryId ?: -1) // Int or -1
                putExtra("course_id", courseItem.id)              // String
                putExtra("title", currentCategory)                 // legacy / compatibility
                putExtra("category", currentCategory)              // nama kategori saat ini (dipakai tvGreeting)
            }
            startActivity(i)
        }

        rvCourses.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvCourses.adapter = adapter
        rvCourses.isNestedScrollingEnabled = false

        // Ensure option buttons don't get tinted by theme and start with inactive background
        val options = listOf(btnOption1, btnOption2, btnOption3, btnOption4)
        val black = ContextCompat.getColor(requireContext(), android.R.color.black)
        options.forEach { btn ->
            btn.backgroundTintList = null
            ViewCompat.setBackgroundTintList(btn, null)
            btn.setBackgroundResource(R.drawable.bg_tab_inactive)
            btn.setTextColor(black)
            btn.isEnabled = true
        }

        try {
            btnQuizAudio.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.white))
            btnQuizAudio.backgroundTintList = null
            ViewCompat.setBackgroundTintList(btnQuizAudio, null)
        } catch (_: Exception) { /* ignore */ }

        updateStarHeartUI()
        setupQuiz()

        // Surah list setup (ViewModel)
        quranViewModel = ViewModelProvider(requireActivity()).get(QuranViewModel::class.java)
        surahAdapter = SurahListAdapter(
            onClick = { surah: SurahSummary ->
                val intent = Intent(requireContext(), SurahDetailActivity::class.java)
                intent.putExtra(SurahDetailActivity.EXTRA_SURAH_NUMBER, surah.number)
                startActivity(intent)
            },
            onPlayClick = { surah: SurahSummary ->
                Toast.makeText(requireContext(), "Play audio: ${surah.number}", Toast.LENGTH_SHORT).show()
            }
        )
        rvSurahList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rvSurahList.adapter = surahAdapter
        rvSurahList.isNestedScrollingEnabled = false

        quranViewModel.surahList.observe(viewLifecycleOwner) { list ->
            try {
                val mapByNumber = list.associateBy { it.number }
                val displayList: List<SurahSummary> = POPULAR_SURAH_IDS.mapNotNull { id -> mapByNumber[id] }
                surahAdapter.submitList(displayList)
            } catch (t: Throwable) {
                Toast.makeText(requireContext(), "Gagal menyiapkan daftar surat: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
        quranViewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show() }
        }
        quranViewModel.loadSurahList()

        // load categories & default courses from Supabase
        loadCategoriesAndDefault()
    }

    // SharedPrefs keys (store both id and name so LearnActivity can fallback)
    private val PREFS_NAME = "home_prefs"
    private val PREF_KEY_SELECTED_CAT = "selected_category_id"
    private val PREF_KEY_SELECTED_CAT_NAME = "selected_category_name"

    // --- Helper to persist last selected category ---
    private fun saveSelectedCategoryId(id: Int) {
        try {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_KEY_SELECTED_CAT, id)
                .apply()
        } catch (_: Exception) { /* ignore if context not ready */ }
    }

    private fun saveSelectedCategoryName(name: String) {
        try {
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SELECTED_CAT_NAME, name)
                .apply()
        } catch (_: Exception) { /* ignore if context not ready */ }
    }

    private fun getSavedSelectedCategoryId(): Int? {
        return try {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains(PREF_KEY_SELECTED_CAT)) prefs.getInt(PREF_KEY_SELECTED_CAT, -1).takeIf { it >= 0 } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun loadCategoriesAndDefault() {
        lifecycleScope.launch {
            val categoriesRes = withContext(Dispatchers.IO) { contentRepo.getCategories() }
            if (categoriesRes.isSuccess) {
                categoriesList = categoriesRes.getOrNull() ?: emptyList()

                // populate dynamic category buttons
                populateCategoryButtons(categoriesList)

                // 1) try to restore previously selected category id
                val savedId = getSavedSelectedCategoryId()
                val savedCat = savedId?.let { id -> categoriesList.firstOrNull { it.id == id } }

                // 2) if no saved, prefer the smallest id (categories are typically ordered by id.asc from API)
                val defaultCat = savedCat ?: categoriesList.minByOrNull { it.id } ?: categoriesList.firstOrNull()

                defaultCat?.let {
                    selectCategory(it)
                } ?: run {
                    // fallback local (very unlikely since categoriesList may be empty)
                    setCategory(currentCategory)
                }
            } else {
                Toast.makeText(requireContext(), "Gagal memuat kategori, menggunakan data lokal.", Toast.LENGTH_SHORT).show()
                setCategory(currentCategory)
            }
        }
    }

    private fun populateCategoryButtons(categories: List<Category>) {
        categoryContainer.removeAllViews()
        val context = requireContext()

        val display = resources.displayMetrics
        val screenWidthPx = display.widthPixels
        val itemFraction = 0.34f
        val buttonWidthPx = (screenWidthPx * itemFraction).toInt()
        val heightPx = (36 * display.density).toInt()

        // jarak antar tombol (hanya dipakai sebagai right margin)
        val gapPx = (16 * display.density).toInt()

        // pastikan container tidak punya padding kiri
        categoryContainer.setPadding(0, 0, gapPx, 0) // tambahkan padding end agar item terakhir tidak nempel

        categories.forEachIndexed { index, cat ->
            val btn = Button(context)

            // margin: 0 left, 0 top, right = gapPx (kecuali item terakhir => 0)
            val rightMargin = if (index == categories.lastIndex) 0 else gapPx
            val lp = LinearLayout.LayoutParams(buttonWidthPx, heightPx)
            lp.setMargins(0, 0, rightMargin, 0)
            btn.layoutParams = lp

            btn.minWidth = (64 * display.density).toInt()
            // padding kiri 0 sesuai keinginanmu, beri padding kanan sedikit untuk teks tidak menempel
            btn.setPadding(0, 0, (12 * display.density).toInt(), 0)

            btn.text = cat.name
            btn.tag = cat.id
            btn.isAllCaps = false

            btn.setBackgroundResource(R.drawable.bg_tab_selector)
            btn.setTextColor(ContextCompat.getColor(context, R.color.tab_text_color))

            btn.setOnClickListener { selectCategory(cat) }

            // mark selected if matches saved or current
            btn.isSelected = (cat.id == selectedCategoryId)
            categoryContainer.addView(btn)
        }
    }

    private fun selectCategory(cat: Category) {
        currentCategory = cat.name
        selectedCategoryId = cat.id

        // persist selected id & name for next app start / navigation
        saveSelectedCategoryId(cat.id)
        saveSelectedCategoryName(cat.name)

        // update button visuals
        for (i in 0 until categoryContainer.childCount) {
            val child = categoryContainer.getChildAt(i) as? Button
            child?.let { btn ->
                val id = btn.tag as? Int
                val isSel = id == selectedCategoryId
                btn.isSelected = isSel
                val white = ContextCompat.getColor(requireContext(), android.R.color.white)
                val normal = ContextCompat.getColor(requireContext(), R.color.tab_text_color)
                btn.setTextColor(if (isSel) white else normal)
            }
        }

        // ensure selected button visible (scroll)
        val selIndex = (0 until categoryContainer.childCount).indexOfFirst { idx ->
            (categoryContainer.getChildAt(idx) as? Button)?.tag as? Int == selectedCategoryId
        }
        if (selIndex >= 0) {
            val selView = categoryContainer.getChildAt(selIndex)
            selView.post {
                val scrollTo = selView.left - (categoryScroll.width - selView.width) / 2
                (categoryScroll as? android.widget.HorizontalScrollView)?.smoothScrollTo(scrollTo.coerceAtLeast(0), 0)
            }
        }

        // load courses for this category
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { contentRepo.getCoursesForCategory(cat.id) }
            if (res.isSuccess) {
                val courses = res.getOrNull() ?: emptyList()
                adapter.updateData(courses)
            } else {
                Toast.makeText(requireContext(), "Gagal memuat kursus: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                adapter.updateData(fallbackData[cat.name] ?: emptyList())
            }
        }
    }

    private fun setCategory(cat: String) {
        currentCategory = cat
        val found = categoriesList.firstOrNull { it.name.equals(cat, true) || it.slug.equals(cat, true) }
        if (found != null) {
            selectCategory(found)
            return
        }
        adapter.updateData(fallbackData[cat] ?: emptyList())
    }

    private fun updateStarHeartUI() {
        tvQuizStarCount.text = starCount.toString()
        tvQuizStarCount.setTypeface(null, Typeface.BOLD)
        tvQuizStarCount.textSize = 14f

        tvQuizHeartCount.text = heartCount.toString()
        tvQuizHeartCount.setTypeface(null, Typeface.BOLD)
        tvQuizHeartCount.textSize = 14f

        val yellow = Color.parseColor("#FFC107")
        val muted = ContextCompat.getColor(requireContext(), R.color.muted_text)
        val heartRed = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)

        val starTint = if (starCount > 0) yellow else muted
        val heartTint = if (heartCount > 0) heartRed else muted

        try {
            ImageViewCompat.setImageTintList(imgQuizStar, ColorStateList.valueOf(starTint))
            ImageViewCompat.setImageTintList(imgQuizHeart, ColorStateList.valueOf(heartTint))
        } catch (_: Exception) {
            try { imgQuizStar.setColorFilter(starTint) } catch (_: Exception) {}
            try { imgQuizHeart.setColorFilter(heartTint) } catch (_: Exception) {}
        }
    }

    private fun setupQuiz() {
        tvQuizQuestion.text = "Dengarkan bacaan berikut ini. Apa hukum tajwid yang terdengar pada potongan ayat tersebut?"
        btnOption1.text = "Idzhar"
        btnOption2.text = "Iqlab"
        btnOption3.text = "Ikhfa"
        btnOption4.text = "Idgham"

        btnQuizAudio.setOnClickListener {
            Toast.makeText(requireContext(), "Play audio (placeholder)", Toast.LENGTH_SHORT).show()
        }

        val optionClick: (Button) -> Unit = { b ->
            val chosen = b.text.toString()
            val isCorrect = chosen == correctAnswer
            handleOptionResult(b, isCorrect)
        }

        btnOption1.setOnClickListener { optionClick(btnOption1) }
        btnOption2.setOnClickListener { optionClick(btnOption2) }
        btnOption3.setOnClickListener { optionClick(btnOption3) }
        btnOption4.setOnClickListener { optionClick(btnOption4) }
    }

    private fun handleOptionResult(selectedButton: Button, isCorrect: Boolean) {
        val options = listOf(btnOption1, btnOption2, btnOption3, btnOption4)
        options.forEach { it.isEnabled = false }

        val white = ContextCompat.getColor(requireContext(), android.R.color.white)

        options.forEach {
            it.backgroundTintList = null
            ViewCompat.setBackgroundTintList(it, null)
        }

        if (isCorrect) {
            selectedButton.setBackgroundResource(R.drawable.bg_tab_active)
            selectedButton.setTextColor(white)
            starCount = (starCount + 1).coerceAtLeast(1)
        } else {
            selectedButton.setBackgroundResource(R.drawable.bg_tab_wrong)
            selectedButton.setTextColor(white)
            vibrateShort()
            heartCount = (heartCount - 1).coerceAtLeast(0)

            options.firstOrNull { it.text.toString() == correctAnswer }?.let { correctBtn ->
                correctBtn.backgroundTintList = null
                ViewCompat.setBackgroundTintList(correctBtn, null)
                correctBtn.setBackgroundResource(R.drawable.bg_tab_active)
                correctBtn.setTextColor(white)
            }
        }

        updateStarHeartUI()
    }

    private fun vibrateShort() {
        val v = context?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
        v?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(200)
            }
        }
    }
}