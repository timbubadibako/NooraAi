package com.example.nooraai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nooraai.data.ContentRepository
import com.example.nooraai.databinding.ActivityLearnBinding
import com.example.nooraai.ui.home.CourseItem
import com.example.nooraai.ui.learn.LessonItem
import com.example.nooraai.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnActivity : BaseActivity() {

    companion object {
        private const val TAG = "LearnActivity"
    }

    private lateinit var binding: ActivityLearnBinding
    private lateinit var materialsAdapter: MaterialsAdapter

    // fallback static lessons used when server has no data or no category_id passed
    private val fallbackLessons = mapOf(
        "Iqra 1" to listOf("Materi 1", "Materi 2", "Materi 3", "Materi 4", "Materi 5"),
        "Iqra 2" to listOf("Materi A", "Materi B", "Materi C"),
        "Iqra 3" to listOf("Materi X", "Materi Y", "Materi Z")
    )

    private var currentLesson = ""
    private var completedCount = 0

    private val contentRepo = ContentRepository()

    // keep list of current courses (tabs)
    private var currentCourseTabs: List<CourseItem> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_learn
    override fun getNavIndex(): Int = 1

    // SharedPrefs keys (used for fallback when Intent doesn't supply category)
    private val PREFS_NAME = "home_prefs"
    private val PREF_KEY_SELECTED_CAT = "selected_category_id"
    private val PREF_KEY_SELECTED_CAT_NAME = "selected_category_name"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val childRoot = getChildRootView()
        binding = ActivityLearnBinding.bind(childRoot)

        // header icons
        val ivCompass = findViewById<ImageView?>(R.id.ivCompass)
        ivCompass?.setOnClickListener {
            com.example.nooraai.ui.compass.KiblatoverlayFragment().show(supportFragmentManager, "kiblat")
        }
        val ivBellHeader = binding.root.findViewById<ImageView?>(R.id.ivBell)
        ivBellHeader?.setOnClickListener {
            com.example.nooraai.ui.prayer.PrayerTimesOverlayFragment().show(supportFragmentManager, "prayer_overlay")
        }

        // --- NEW: fill location chip (same UX as MainActivity) ---
        val tvLocationText = binding.root.findViewById<TextView>(R.id.tvLocationText)
        if (tvLocationText != null) {
            // fetch location and update the chip text; callback receives lat/lon when saved
            LocationHelper.fetchAndFillLocation(this, tvLocationText) { lat, lon ->
                Log.d(TAG, "location saved: $lat, $lon")
                // If you want, notify fragments or refresh Qibla overlay here.
            }

            // Optional: allow user to tap the location chip to refresh location
            val locationChip = binding.root.findViewById<View>(R.id.location_chip)
            locationChip?.setOnClickListener {
                LocationHelper.fetchAndFillLocation(this, tvLocationText) { lat, lon ->
                    Log.d(TAG, "manual refresh location saved: $lat, $lon")
                }
            }
        }
        // --- END NEW ---

        // Setup UI components
        materialsAdapter = MaterialsAdapter(emptyList())
        binding.rvMaterials.layoutManager = LinearLayoutManager(this)
        binding.rvMaterials.adapter = materialsAdapter
        binding.rvMaterials.isNestedScrollingEnabled = false

        binding.cardExam.setOnClickListener {
            Toast.makeText(this, "Open Ujian untuk $currentLesson", Toast.LENGTH_SHORT).show()
        }
        binding.cardProgress.setOnClickListener {
            Toast.makeText(this, "Progress: $completedCount dari ${materialsAdapter.itemCount}", Toast.LENGTH_SHORT).show()
        }

        // Read intent extras
        val courseIdFromIntent = intent?.getStringExtra("course_id") ?: ""
        val categoryIdFromIntent = intent?.getIntExtra("category_id", -1)?.takeIf { it != -1 }
        val categoryNameFromIntent = intent?.getStringExtra("category")

        // Decide category name to display: intent -> saved -> default
        val savedName = getSavedSelectedCategoryName()
        val displayedCategoryName = categoryNameFromIntent ?: savedName ?: "Pembelajaran"
        binding.tvLearnTitle.text = "Pembelajaran"
        binding.tvGreeting.text = displayedCategoryName

        // Flow:
        if (categoryIdFromIntent != null) {
            saveSelectedCategoryId(categoryIdFromIntent)
            saveSelectedCategoryName(displayedCategoryName)
            loadCoursesForCategoryAndSelect(categoryIdFromIntent, courseIdFromIntent.ifBlank { null })
        } else {
            val savedCatId = getSavedSelectedCategoryId()
            if (savedCatId != null) {
                binding.tvGreeting.text = getSavedSelectedCategoryName() ?: displayedCategoryName
                loadCoursesForCategoryAndSelect(savedCatId, courseIdFromIntent.ifBlank { null })
            } else {
                lifecycleScope.launch {
                    val catsRes = withContext(Dispatchers.IO) { contentRepo.getCategories() }
                    val cats = catsRes.getOrNull() ?: emptyList()

                    val minCat = cats.minByOrNull { it.id }
                    if (minCat != null) {
                        saveSelectedCategoryId(minCat.id)
                        saveSelectedCategoryName(minCat.name)
                        binding.tvGreeting.text = minCat.name
                        loadCoursesForCategoryAndSelect(minCat.id, courseIdFromIntent.ifBlank { null })
                    } else {
                        setupLessonTabs(emptyList(), 0)
                        showLessonList(emptyList())
                    }
                }
            }
        }
    }

    /**
     * Forward permission result to LocationHelper so fetchAndFillLocation can continue after user grants.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // forward to LocationHelper (it checks the request code internally)
        val tvLocationText = binding.root.findViewById<TextView>(R.id.tvLocationText)
        LocationHelper.onRequestPermissionsResult(requestCode, permissions, grantResults, this, tvLocationText) { lat, lon ->
            Log.d(TAG, "location after permission: $lat,$lon")
        }
    }

    /**
     * Fetch courses for the given category, set up tabs from those courses, select initial based on selectedCourseId,
     * and load lessons for the selected course.
     */
    private fun loadCoursesForCategoryAndSelect(categoryId: Int, selectedCourseId: String?) {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                contentRepo.getCoursesForCategory(categoryId)
            }

            if (res.isSuccess) {
                val courses = res.getOrNull() ?: emptyList()
                if (courses.isNotEmpty()) {
                    currentCourseTabs = courses
                    val initialIndex = if (!selectedCourseId.isNullOrBlank()) {
                        courses.indexOfFirst { it.id == selectedCourseId }.takeIf { it >= 0 } ?: 0
                    } else 0

                    setupCourseTabs(courses, initialIndex)

                    val initialCourseId = courses.getOrNull(initialIndex)?.id ?: ""
                    if (initialCourseId.isNotBlank()) {
                        loadLessonsForCourseId(initialCourseId)
                    } else {
                        showLessonList(emptyList())
                    }
                    return@launch
                }
            }

            Toast.makeText(this@LearnActivity, "Tidak dapat memuat daftar kursus. Menampilkan fallback.", Toast.LENGTH_SHORT).show()
            val fallback = fallbackLessons[displayedCategoryNameFromPrefsOrIntent()] ?: emptyList()
            // create dummy LessonItem objects from fallback strings
            val dummyLessons = fallback.mapIndexed { idx, t ->
                LessonItem(id = "fallback-$idx", title = t, description = null, sortOrder = idx, courseId = null)
            }
            setupLessonTabs(fallback, 0)
            showLessonList(dummyLessons)
        }
    }

    private fun displayedCategoryNameFromPrefsOrIntent(): String =
        intent?.getStringExtra("category") ?: getSavedSelectedCategoryName() ?: "Pembelajaran"

    /**
     * Create tabs from CourseItem list.
     */
    private fun setupCourseTabs(courses: List<CourseItem>, initialIndex: Int) {
        val container = findViewById<LinearLayout>(R.id.lessons_tab_row_container) ?: return
        container.removeAllViews()
        container.setPadding(0, 0, 0, 0)

        val context = this
        val density = resources.displayMetrics.density
        val fixedWidthPx = (180 * density).toInt()
        val paddingHorizontal = dpToPx(12)
        val gap = dpToPx(6)

        courses.forEachIndexed { index, course ->
            val btn = Button(context)
            val lp = LinearLayout.LayoutParams(fixedWidthPx, LinearLayout.LayoutParams.MATCH_PARENT)
            if (courses.size > 3) lp.setMargins(gap, 0, gap, 0) else lp.setMargins(0, 0, 0, 0)

            btn.layoutParams = lp
            btn.text = course.title
            btn.isAllCaps = false
            btn.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
            btn.setBackgroundResource(R.drawable.tab_rectangle_inactive)
            btn.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

            btn.setOnClickListener {
                setSelectedTabDynamic(btn, container)
                val courseId = courses[index].id
                loadLessonsForCourseId(courseId)
            }

            container.addView(btn)
        }

        val idx = initialIndex.coerceIn(0, (courses.size - 1).coerceAtLeast(0))
        if (container.childCount > 0) {
            val initialBtn = container.getChildAt(idx) as? Button
            initialBtn?.let { setSelectedTabDynamic(it, container) }
        }
    }

    // show a single lesson (used by fallback tabs)
    private fun showLesson(lessonName: String) {
        currentLesson = lessonName
        val list = fallbackLessons[lessonName] ?: emptyList()
        val dummy = list.mapIndexed { idx, t ->
            LessonItem(id = "fallback-$idx", title = t, description = null, sortOrder = idx, courseId = null)
        }
        materialsAdapter.update(dummy)
        binding.tvProgressTitle.text = "$completedCount dari ${dummy.size} Materi"
    }

    private fun setupLessonTabs(titles: List<String>, initialIndex: Int) {
        val container = findViewById<LinearLayout>(R.id.lessons_tab_row_container) ?: return
        container.removeAllViews()

        val context = this
        val marginPx = dpToPx(8)
        val paddingHorizontal = dpToPx(16)
        val density = resources.displayMetrics.density
        val minWidthPx = (120 * density).toInt()

        titles.forEachIndexed { index, title ->
            val btn = Button(context)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            lp.setMargins(marginPx, marginPx, marginPx, marginPx)
            btn.layoutParams = lp
            btn.text = title
            btn.isAllCaps = false
            btn.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
            btn.minWidth = minWidthPx

            btn.setBackgroundResource(R.drawable.tab_rectangle_inactive)
            btn.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

            btn.setOnClickListener {
                setSelectedTabDynamic(btn, container)
                showLesson(title)
            }

            container.addView(btn)
        }

        val idx = initialIndex.coerceIn(0, (titles.size - 1).coerceAtLeast(0))
        if (container.childCount > 0) {
            val initialBtn = container.getChildAt(idx) as? Button
            initialBtn?.let { setSelectedTabDynamic(it, container) }
        }
    }

    private fun setupSingleTabAndLoad(courseId: String, courseTitle: String) {
        val container = findViewById<LinearLayout>(R.id.lessons_tab_row_container) ?: return
        container.removeAllViews()
        val btn = Button(this)
        btn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
            setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        btn.text = courseTitle
        btn.isAllCaps = false
        btn.setPadding(dpToPx(16), 0, dpToPx(16), 0)
        btn.setBackgroundResource(R.drawable.tab_rectangle_active)
        btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        container.addView(btn)

        loadLessonsForCourseId(courseId)
    }

    /**
     * Load lessons for specific course id and display them. Uses ContentRepository.getLessonsForCourse.
     * Also prefetches lesson parts counts for all lessons and updates the adapter.
     */
    private fun loadLessonsForCourseId(courseId: String) {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                contentRepo.getLessonsForCourse(courseId)
            }

            if (res.isSuccess) {
                val list = res.getOrNull() ?: emptyList()
                showLessonList(list)

                // Prefetch parts counts concurrently (IO) and update adapter with map
                if (list.isNotEmpty()) {
                    val deferred = list.map { lesson ->
                        async(Dispatchers.IO) {
                            val partsRes = contentRepo.getLessonParts(lesson.id)
                            val count = if (partsRes.isSuccess) partsRes.getOrNull()?.size ?: 0 else 0
                            lesson.id to count
                        }
                    }
                    val pairs = deferred.awaitAll()
                    val countsMap = pairs.toMap()
                    // update adapter on main thread
                    withContext(Dispatchers.Main) {
                        materialsAdapter.setPartsCountMap(countsMap)
                    }
                } else {
                    // ensure map cleared when no lessons
                    materialsAdapter.setPartsCountMap(emptyMap())
                }
            } else {
                Toast.makeText(this@LearnActivity, "Gagal memuat materi: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                showLessonList(emptyList())
            }
        }
    }

    /**
     * Select given tab among dynamic buttons in container and scroll it when necessary.
     */
    private fun setSelectedTabDynamic(selectedBtn: Button, container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? Button ?: continue
            if (child == selectedBtn) {
                child.setBackgroundResource(R.drawable.tab_rectangle_active)
                child.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                child.setBackgroundResource(R.drawable.tab_rectangle_inactive)
                child.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }

        val scrollView = findViewById<HorizontalScrollView>(R.id.lessonsTabScroll)
        scrollView?.post {
            val svWidth = scrollView.width
            val containerWidth = container.width
            if (containerWidth > svWidth) {
                selectedBtn.post {
                    val targetX = (selectedBtn.left + selectedBtn.right - svWidth) / 2
                    scrollView.smoothScrollTo(targetX.coerceAtLeast(0), 0)
                }
            } else {
                scrollView.smoothScrollTo(0, 0)
            }
        }
    }

    // showLessonList now accepts List<LessonItem>
    private fun showLessonList(list: List<LessonItem>) {
        currentLesson = list.firstOrNull()?.title ?: ""
        materialsAdapter.update(list)
        binding.tvProgressTitle.text = "$completedCount dari ${list.size} Materi"
    }

    // Adapter now works with LessonItem objects and opens LessonActivity on click
    class MaterialsAdapter(private var items: List<LessonItem>) : androidx.recyclerview.widget.RecyclerView.Adapter<MaterialsAdapter.VH>() {

        private var partsCountMap: Map<String, Int> = emptyMap()

        fun setPartsCountMap(map: Map<String, Int>) {
            partsCountMap = map
            notifyDataSetChanged()
        }

        fun update(newItems: List<LessonItem>) {
            items = newItems ?: emptyList()
            // clear counts when dataset changes (optional)
            // partsCountMap = partsCountMap.filterKeys { k -> items.any { it.id == k } }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_material, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            if (items.isEmpty()) holder.bindPlaceholder()
            else holder.bind(items[position], partsCountMap)
        }

        override fun getItemCount(): Int = if (items.isEmpty()) 1 else items.size

        class VH(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.tvMaterialTitle)
            private val subtitle: TextView = itemView.findViewById(R.id.tvMaterialSubtitle)
            private val info: TextView? = itemView.findViewById(R.id.tvMaterialInfo)

            fun bind(lesson: LessonItem, counts: Map<String, Int>) {
                title.text = lesson.title ?: "Untitled"
                val desc = lesson.description ?: ""
                subtitle.text = if (desc.isNotBlank()) desc else "Ringkasan singkat"

                // Set the info/count text
                val count = counts[lesson.id] ?: 0
                info?.text = "$count Pembelajaran"

                // Open LessonActivity when tapping either play button or whole item
                val openLesson: (View) -> Unit = {
                    val ctx = itemView.context
                    val intent = Intent(ctx, LessonActivity::class.java).apply {
                        putExtra("lesson_id", lesson.id)
                        // pass existing sort order so LessonActivity can initialize header without re-fetch
                        putExtra("lesson_sort_order", lesson.sortOrder ?: -1)
                    }
                    ctx.startActivity(intent)
                }

                itemView.setOnClickListener(openLesson)
            }

            fun bindPlaceholder() {
                title.text = "Belum ada materi"
                subtitle.text = ""
                info?.text = "0 Pembelajaran"
                itemView.setOnClickListener(null)
            }
        }
    }

    // helpers: prefs read/save used when Intent doesn't carry category
    private fun getSavedSelectedCategoryId(): Int? {
        return try {
            val p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (p.contains(PREF_KEY_SELECTED_CAT)) p.getInt(PREF_KEY_SELECTED_CAT, -1).takeIf { it >= 0 } else null
        } catch (_: Exception) { null }
    }

    private fun getSavedSelectedCategoryName(): String? {
        return try {
            val p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            p.getString(PREF_KEY_SELECTED_CAT_NAME, null)
        } catch (_: Exception) { null }
    }

    private fun saveSelectedCategoryId(id: Int) {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_KEY_SELECTED_CAT, id)
                .apply()
        } catch (_: Exception) { /* ignore */ }
    }

    private fun saveSelectedCategoryName(name: String) {
        try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SELECTED_CAT_NAME, name)
                .apply()
        } catch (_: Exception) { /* ignore */ }
    }

    // Helper: dp -> px
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}