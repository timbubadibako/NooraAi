package com.example.nooraai.ui.detail

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import com.example.nooraai.model.AyatItem

class AyatAdapter(
    private var items: List<AyatItem>,
    private var lastReadAyahNumber: Int = -1,
    private val onPlayClick: (AyatItem, Int) -> Unit,
    private val onCardClick: (AyatItem, Int) -> Unit,
    private val onSaveClick: (AyatItem, Int) -> Unit // new: save (bookmark) click
) : RecyclerView.Adapter<AyatAdapter.VH>() {

    private var contextRef: Context? = null

    // UI state
    private var playingIndex: Int = -1
    private var isPlaying: Boolean = false
    private var previewIndex: Int = -1

    // saved/bookmarked ayah for this loaded surah (ayah number)
    private var savedAyahNumber: Int = -1

    fun updateData(newItems: List<AyatItem>, lastRead: Int = lastReadAyahNumber) {
        items = newItems
        lastReadAyahNumber = lastRead
        notifyDataSetChanged()
    }

    fun setLastReadAyah(ayahNumber: Int) {
        val prev = lastReadAyahNumber
        lastReadAyahNumber = ayahNumber
        if (prev != -1) {
            val prevPos = items.indexOfFirst { it.ayahNumber == prev }
            if (prevPos >= 0) notifyItemChanged(prevPos)
        }
        val newPos = items.indexOfFirst { it.ayahNumber == ayahNumber }
        if (newPos >= 0) notifyItemChanged(newPos)
    }

    fun setPreviewIndex(index: Int) {
        val prev = previewIndex
        previewIndex = index
        if (prev >= 0) notifyItemChanged(prev)
        if (previewIndex >= 0) notifyItemChanged(previewIndex)
    }

    fun setPlayingState(index: Int, playing: Boolean) {
        val prev = playingIndex
        playingIndex = index
        isPlaying = playing
        if (prev >= 0) notifyItemChanged(prev)
        if (playingIndex >= 0) notifyItemChanged(playingIndex)
    }

    fun setSavedAyah(ayahNumber: Int) {
        val prev = savedAyahNumber
        savedAyahNumber = ayahNumber
        if (prev >= 0) {
            val prevPos = items.indexOfFirst { it.ayahNumber == prev }
            if (prevPos >= 0) notifyItemChanged(prevPos)
        }
        if (savedAyahNumber >= 0) {
            val newPos = items.indexOfFirst { it.ayahNumber == savedAyahNumber }
            if (newPos >= 0) notifyItemChanged(newPos)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        contextRef = recyclerView.context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ayat, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ayat = items[position]
        val isLastRead = ayat.ayahNumber == lastReadAyahNumber
        val isPreview = position == previewIndex
        val isCurrentlyPlaying = position == playingIndex && isPlaying
        val isSaved = ayat.ayahNumber == savedAyahNumber
        holder.bind(ayat, position, isLastRead, isPreview, isCurrentlyPlaying, isSaved)
    }

    override fun getItemCount(): Int = items.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val card: CardView? = v as? CardView
        private val tvBadgeNumber: TextView = v.findViewById(R.id.tvAyatBadgeNumber)
        private val tvArab: TextView = v.findViewById(R.id.tvAyatText)
        private val tvLatin: TextView = v.findViewById(R.id.tvAyatLatin)
        private val tvTranslation: TextView = v.findViewById(R.id.tvAyatTranslate)
        private val ivPlay: ImageView = v.findViewById(R.id.ivAyatPlay)
        private val ivSave: ImageView = v.findViewById(R.id.ivAyatSave)

        fun bind(
            ayat: AyatItem,
            pos: Int,
            isLastRead: Boolean,
            isPreview: Boolean,
            isCurrentlyPlaying: Boolean,
            isSaved: Boolean
        ) {
            tvBadgeNumber.text = ayat.ayahNumber.toString()
            tvArab.text = ayat.arab ?: ""
            tvLatin.text = ayat.latin ?: ""
            tvTranslation.text = ayat.translation ?: ""

            val ctx = contextRef ?: itemView.context
            val playingColor = ContextCompat.getColor(ctx, R.color.playing_card_bg)
            val previewColor = ContextCompat.getColor(ctx, R.color.preview_card_bg)
            val lastReadColor = ContextCompat.getColor(ctx, R.color.last_read_highlight)
            val normalColor = ContextCompat.getColor(ctx, R.color.card_white)

            card?.setCardBackgroundColor(
                when {
                    isCurrentlyPlaying -> playingColor
                    isPreview -> previewColor
                    isLastRead -> lastReadColor
                    else -> normalColor
                }
            )

            ivPlay.setImageResource(
                if (isCurrentlyPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            // update save icon (bookmark) visual
            ivSave.setImageResource(if (isSaved) R.drawable.marked else R.drawable.mark)
            val tintColor = if (isSaved) R.color.primary_color else R.color.primary_50
            ivSave.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, tintColor))

            // Card click: select & save last-read (but do NOT play)
            card?.setOnClickListener {
                animateTap(it) {
                    onCardClick(ayat, pos)
                }
            }

            // Play button: play single ayat (host activity handles actual playback)
            ivPlay.setOnClickListener {
                animateTap(it) {
                    onPlayClick(ayat, pos)
                }
            }

            // Save (bookmark) button
            ivSave.setOnClickListener {
                animateTap(it) {
                    onSaveClick(ayat, pos)
                }
            }
        }

        private fun animateTap(target: View, onEnd: () -> Unit) {
            val sx = ObjectAnimator.ofFloat(target, View.SCALE_X, 0.96f).apply {
                duration = 80
                interpolator = DecelerateInterpolator()
            }
            val sy = ObjectAnimator.ofFloat(target, View.SCALE_Y, 0.96f).apply {
                duration = 80
                interpolator = DecelerateInterpolator()
            }
            val sx2 = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f).apply {
                duration = 120
                interpolator = DecelerateInterpolator()
            }
            val sy2 = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f).apply {
                duration = 120
                interpolator = DecelerateInterpolator()
            }

            sx.start(); sy.start()
            sx.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    sx2.start(); sy2.start()
                    sx2.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onEnd()
                        }
                    })
                }
            })
        }
    }
}