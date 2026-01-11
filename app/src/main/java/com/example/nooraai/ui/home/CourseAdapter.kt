package com.example.nooraai.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import kotlin.math.roundToInt

/**
 * CourseAdapter that reuses the same item layout for both real items and the "null/placeholder" state.
 * When the data list is empty the adapter still shows one card (using the same layout) but populated
 * with placeholder values and neutral gray gradient. Clicks are disabled for the placeholder card.
 */
class CourseAdapter(
    private val onClick: (CourseItem) -> Unit
) : RecyclerView.Adapter<CourseAdapter.ItemVH>() {

    private var items: List<CourseItem> = emptyList()

    companion object {
        private const val FALLBACK_LEFT = "#06B6D4"
        private const val FALLBACK_RIGHT = "#0EA5A4"
        private const val PLACEHOLDER_LEFT = "#EDEDED"
        private const val PLACEHOLDER_RIGHT = "#E0E0E0"
    }

    inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val root: View = view.findViewById(R.id.course_card)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        private val tvCount: TextView = view.findViewById(R.id.tvCount)

        fun bind(ci: CourseItem) {
            // Normal item binding
            tvTitle.text = ci.title
            tvCount.text = when (ci.lessons) {
                null -> ""
                0 -> "0 Materi"
                1 -> "1 Materi"
                else -> "${ci.lessons} Materi"
            }

            if (ci.level != null) {
                tvLevel.visibility = View.VISIBLE
                tvLevel.text = "Level ${ci.level}"
            } else {
                tvLevel.visibility = View.GONE
            }

            val leftHex = ci.leftColor ?: FALLBACK_LEFT
            val rightHex = ci.rightColor ?: FALLBACK_RIGHT
            val leftInt = parseColorSafe(leftHex, Color.parseColor(FALLBACK_LEFT))
            val rightInt = parseColorSafe(rightHex, Color.parseColor(FALLBACK_RIGHT))

            val gd = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(leftInt, rightInt))
            gd.cornerRadius = dpToPx(12f).toFloat()
            root.background = gd

            val textColor = pickReadableTextColor(leftInt, rightInt)
            tvTitle.setTextColor(textColor)
            tvLevel.setTextColor(textColor)
            tvCount.setTextColor(textColor)

            itemView.isClickable = true
            itemView.setOnClickListener { onClick(ci) }
        }

        fun bindPlaceholder() {
            // Placeholder binding using same layout and ids
            tvTitle.text = "Data masih kosong"
            tvLevel.visibility = View.GONE
            tvCount.text = "" // or "0 Materi" if preferred

            val leftInt = parseColorSafe(PLACEHOLDER_LEFT, Color.parseColor(PLACEHOLDER_LEFT))
            val rightInt = parseColorSafe(PLACEHOLDER_RIGHT, Color.parseColor(PLACEHOLDER_RIGHT))
            val gd = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(leftInt, rightInt))
            gd.cornerRadius = dpToPx(12f).toFloat()
            root.background = gd

            // placeholder text colors: darker gray for title, muted for count (use single color)
            val titleColor = parseColorSafe("#666666", Color.DKGRAY)
            tvTitle.setTextColor(titleColor)
            tvCount.setTextColor(titleColor)

            // disable interaction
            itemView.isClickable = false
            itemView.setOnClickListener(null)
        }

        private fun dpToPx(dp: Float): Int =
            (dp * itemView.resources.displayMetrics.density).roundToInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
        val inflater = LayoutInflater.from(parent.context)
        val v = inflater.inflate(R.layout.layout_course_card, parent, false) // same layout used for placeholder
        return ItemVH(v)
    }

    override fun getItemCount(): Int = if (items.isEmpty()) 1 else items.size

    override fun onBindViewHolder(holder: ItemVH, position: Int) {
        if (items.isEmpty()) {
            holder.bindPlaceholder()
        } else {
            holder.bind(items[position])
        }
    }

    /**
     * Replace adapter data. If provided list is empty, adapter will show one placeholder card using the same layout.
     */
    fun updateData(newItems: List<CourseItem>) {
        this.items = newItems ?: emptyList()
        notifyDataSetChanged()
    }

    // helpers
    private fun parseColorSafe(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try { Color.parseColor(hex) } catch (_: Throwable) { fallback }
    }

    private fun pickReadableTextColor(c1: Int, c2: Int): Int {
        val lum = (luminance(c1) + luminance(c2)) / 2f
        return if (lum < 0.55f) Color.WHITE else Color.parseColor("#222222")
    }

    private fun luminance(color: Int): Float {
        fun channel(c: Int): Float {
            val v = (Color.red(c) / 255.0).toFloat()
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * channel(color) + 0.7152f * channel(color) + 0.0722f * channel(color)
    }
}