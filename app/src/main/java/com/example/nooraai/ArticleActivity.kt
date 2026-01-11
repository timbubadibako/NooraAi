package com.example.nooraai

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.nooraai.data.Article
import com.example.nooraai.data.AudioFile
import com.example.nooraai.data.ContentRepository

/**
 * ArticleActivity
 * Expects intent extras:
 * - "lesson_part_id" : String (UUID)  -- required
 * - "lesson_part_title" : String      -- optional (used for header)
 *
 * Notes:
 * - ContentRepository must implement:
 *   suspend fun getArticlesForPart(lessonPartId: String): Result<List<Article>>
 *   suspend fun getAudioFilesForArticle(articleId: String): Result<List<AudioFile>>
 */
class ArticleActivity : BaseActivity() {

    private val contentRepo = ContentRepository()
    private var mediaPlayer: MediaPlayer? = null

    override fun getLayoutId(): Int = R.layout.activity_article
    override fun getNavIndex(): Int = 1

    private lateinit var tvPartTitle: TextView
    private lateinit var containerArticles: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = getChildRootView()
        tvPartTitle = root.findViewById(R.id.tvPartTitle)
        containerArticles = root.findViewById(R.id.containerArticles)

        val partId = intent?.getStringExtra("lesson_part_id")
        val partTitle = intent?.getStringExtra("lesson_part_title") ?: "Materi"

        tvPartTitle.text = partTitle

        if (partId.isNullOrBlank()) {
            Toast.makeText(this, "Bagian materi tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        loadArticlesForPart(partId)
    }

    private fun loadArticlesForPart(partId: String) {
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) { contentRepo.getArticlesForPart(partId) }
            if (res.isSuccess) {
                val articles = res.getOrNull() ?: emptyList()
                displayArticles(articles)
            } else {
                Toast.makeText(this@ArticleActivity, "Gagal memuat artikel: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayArticles(articles: List<Article>) {
        containerArticles.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (article in articles) {
            val v = inflater.inflate(R.layout.item_article, containerArticles, false)
            val tvTitle = v.findViewById<TextView>(R.id.tvArticleTitle)
            val tvContent = v.findViewById<TextView>(R.id.tvArticleContent)
            val btnPlayExample = v.findViewById<Button>(R.id.btnPlayExample)

            tvTitle.text = article.title
            // content may contain HTML; use Html.fromHtml for basic rendering
            tvContent.text = if (!article.content.isNullOrBlank()) {
                Html.fromHtml(article.content, Html.FROM_HTML_MODE_LEGACY)
            } else {
                article.excerpt ?: ""
            }

            // default hide play button until we check audio files
            btnPlayExample.visibility = View.GONE

            // fetch audio files for this article (example audio)
            lifecycleScope.launch {
                val audRes = withContext(Dispatchers.IO) { contentRepo.getAudioFilesForArticle(article.id) }
                if (audRes.isSuccess) {
                    val audList = audRes.getOrNull() ?: emptyList()
                    // choose first audio file as example (or filter by type == "example")
                    val ex = audList.firstOrNull { it.type == "example" } ?: audList.firstOrNull()
                    if (ex != null && !ex.storage_path.isNullOrBlank()) {
                        btnPlayExample.visibility = View.VISIBLE
                        btnPlayExample.setOnClickListener {
                            playUrl(ex.storage_path)
                        }
                    } else {
                        btnPlayExample.visibility = View.GONE
                    }
                } else {
                    btnPlayExample.visibility = View.GONE
                }
            }

            containerArticles.addView(v)
        }

        if (articles.isEmpty()) {
            // optional: show placeholder text
            val v = inflater.inflate(R.layout.item_article, containerArticles, false)
            val tvTitle = v.findViewById<TextView>(R.id.tvArticleTitle)
            val tvContent = v.findViewById<TextView>(R.id.tvArticleContent)
            val btnPlayExample = v.findViewById<Button>(R.id.btnPlayExample)
            tvTitle.text = "Belum ada artikel"
            tvContent.text = ""
            btnPlayExample.visibility = View.GONE
            containerArticles.addView(v)
        }
    }

    private fun playUrl(url: String) {
        try {
            // release previous
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { /* optional UI update */ }
                prepareAsync()
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Gagal memutar audio: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}