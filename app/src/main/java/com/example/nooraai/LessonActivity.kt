package com.example.nooraai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.data.ContentRepository
import com.example.nooraai.data.LessonPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LessonActivity : BaseActivity() {

    private val TAG = "LessonActivity"
    private val contentRepo = ContentRepository()

    private lateinit var rvParts: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var progress: ProgressBar

    // header views
    private lateinit var ivBack: ImageView
    private lateinit var tvSortOrder: TextView

    // adapter
    private lateinit var partsAdapter: PartsAdapter

    // initial sort order passed from previous screen (if any)
    private var initialSortFromIntent: Int = -1

    override fun getLayoutId(): Int = R.layout.activity_lesson
    override fun getNavIndex(): Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = getChildRootView()
        rvParts = root.findViewById(R.id.rvParts)
        tvTitle = root.findViewById(R.id.tvLessonTitle)
        tvDescription = root.findViewById(R.id.tvLessonDesc)
        progress = root.findViewById(R.id.progressLoading)

        ivBack = root.findViewById(R.id.ivBack)
        tvSortOrder = root.findViewById(R.id.tvSortOrder)

        // read initial sort order from Intent extras (if provided)
        initialSortFromIntent = intent?.getIntExtra("lesson_sort_order", -1) ?: -1
        Log.d(TAG, "Initial sort from Intent = $initialSortFromIntent")
        if (initialSortFromIntent > 0) {
            tvSortOrder.text = "Materi ke $initialSortFromIntent"
        } else {
            // temporary placeholder until parts load
            tvSortOrder.text = "Materi ke 0"
        }

        // back button
        ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        rvParts.layoutManager = LinearLayoutManager(this)
        // adapter callback now provides the clicked LessonPart
        partsAdapter = PartsAdapter(emptyList()) { clickedPart ->
            // update header sort order when user selects a part
            val s = clickedPart.sort_order ?: -1
            tvSortOrder.text = if (s > 0) "Materi ke ($s)" else "Materi"

            // open ArticleActivity
            val i = Intent(this, ArticleActivity::class.java).apply {
                putExtra("lesson_part_id", clickedPart.id)
                putExtra("lesson_part_title", clickedPart.title ?: "")
            }
            startActivity(i)
        }
        rvParts.adapter = partsAdapter

        val lessonId = intent?.getStringExtra("lesson_id")
        if (lessonId.isNullOrBlank()) {
            Toast.makeText(this, "Lesson ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        loadLessonAndParts(lessonId)
    }

    private fun loadLessonAndParts(lessonId: String) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            // ambil metadata lesson
            val lessonRes = withContext(Dispatchers.IO) { contentRepo.getLessonById(lessonId) }
            if (lessonRes.isSuccess) {
                val lesson = lessonRes.getOrNull()
                tvTitle.text = lesson?.title ?: "Pembelajaran"
                tvDescription.text = lesson?.description ?: ""
            } else {
                tvTitle.text = "Pembelajaran"
                tvDescription.text = ""
            }

            val partsRes = withContext(Dispatchers.IO) { contentRepo.getLessonParts(lessonId) }
            progress.visibility = View.GONE
            if (partsRes.isSuccess) {
                val parts = partsRes.getOrNull() ?: emptyList()

                // debug: log all sort_order values we received
                Log.d(TAG, "Loaded parts count=${parts.size} sort_orders=${parts.map { it.sort_order }}")

                partsAdapter.update(parts)

                // Only override the header sort order if caller didn't already provide one
                if (initialSortFromIntent <= 0) {
                    // set initial tvSortOrder to first part's sort_order if present
                    val initialSort = parts.firstOrNull()?.sort_order
                    tvSortOrder.text = if (initialSort != null && initialSort > 0) "Materi ke ($initialSort)" else "Materi ke (1)"
                } else {
                    // keep the value passed via Intent
                    Log.d(TAG, "Keeping initial sort from intent: $initialSortFromIntent")
                }
            } else {
                Toast.makeText(this@LessonActivity, "Gagal memuat bagian: ${partsRes.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                partsAdapter.update(emptyList())
                // if caller didn't pass initial sort, keep fallback; otherwise keep the passed value
                if (initialSortFromIntent <= 0) tvSortOrder.text = "Materi ke (1)"
            }
        }
    }

    // Recycler adapter for lesson parts. Callback provides the clicked LessonPart
    class PartsAdapter(
        private var items: List<LessonPart>,
        private val onClick: (LessonPart) -> Unit
    ) : RecyclerView.Adapter<PartsAdapter.VH>() {

        fun update(newItems: List<LessonPart>) {
            items = newItems ?: emptyList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lesson_part, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = items[position]
            holder.title.text = p.title ?: "Bagian ${position + 1}"
            holder.subtitle.text = p.description ?: ""

            if (p.sort_order != null) {
                holder.meta.visibility = View.VISIBLE
                holder.meta.text = "Bagian ${p.sort_order}"
            } else {
                holder.meta.visibility = View.GONE
            }

            holder.chevron.visibility = View.VISIBLE

            // when clicked, pass whole LessonPart to Activity via callback
            holder.itemView.setOnClickListener { onClick(p) }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvPartTitle)
            val subtitle: TextView = v.findViewById(R.id.tvPartSubtitle)
            val meta: TextView = v.findViewById(R.id.tvPartMeta)
            val chevron: ImageView = v.findViewById(R.id.ivPartChevron)
        }
    }
}