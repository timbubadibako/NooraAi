package com.example.nooraai.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import com.example.nooraai.model.SurahSummary

class SurahListAdapter(
    private val onClick: (SurahSummary) -> Unit,
    private val onPlayClick: (SurahSummary) -> Unit
) : ListAdapter<SurahSummary, SurahListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SurahSummary>() {
            override fun areItemsTheSame(oldItem: SurahSummary, newItem: SurahSummary): Boolean =
                oldItem.number == newItem.number

            override fun areContentsTheSame(oldItem: SurahSummary, newItem: SurahSummary): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_surah_list, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNumber: TextView? = itemView.findViewById(R.id.tvSurahNumberBox)
        private val tvTitle: TextView? = itemView.findViewById(R.id.tvSurahTitle)
        private val tvSubtitle: TextView? = itemView.findViewById(R.id.tvSurahSubtitle)
        private val ivPlayMic: ImageView? = itemView.findViewById(R.id.ivPlayMic)
        private val tvProgressPercent: TextView? = itemView.findViewById(R.id.tvProgressPercent)

        fun bind(surah: SurahSummary) {
            tvNumber?.text = surah.number.toString()

            // Tampilkan "nama biasa" pada judul (mis. bahasa indonesia / english).
            // Jika nama kosong, fallback pakai transliteration.
            tvTitle?.text = surah.name.ifBlank { surah.transliteration ?: "Surah ${surah.number}" }

            // Subtitle: tampilkan transliteration (latin) kalau ada, kalau tidak tampilkan terjemahan
            tvSubtitle?.text = surah.translation?.takeIf { it.isNotBlank() }
                ?: surah.translation
                        ?: ""

            // Jika tidak ada progress data, sembunyikan progress
            tvProgressPercent?.visibility = View.GONE

            itemView.setOnClickListener { onClick(surah) }
            ivPlayMic?.setOnClickListener { onPlayClick(surah) }
        }
    }
}