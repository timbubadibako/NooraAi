package com.example.nooraai.ui.prayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import com.example.nooraai.ui.prayer.PrayerTimesAdapter.PrayerViewHolder
import com.example.nooraai.model.PrayerItem

class PrayerTimesAdapter(
    val ctx: Context,
    private val onBellClick: (PrayerItem, Int) -> Unit
) : ListAdapter<PrayerItem, PrayerViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PrayerItem>() {
            override fun areItemsTheSame(oldItem: PrayerItem, newItem: PrayerItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: PrayerItem, newItem: PrayerItem) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrayerViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_prayer_time, parent, false)
        return PrayerViewHolder(v)
    }

    override fun onBindViewHolder(holder: PrayerViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class PrayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvPrayerName)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvPrayerTime)
        private val ivBell = itemView.findViewById<ImageView>(R.id.ivBell)
        private val card = itemView.findViewById<View>(R.id.cardRoot)

        fun bind(item: PrayerItem, pos: Int) {
            tvName.text = item.name
            tvTime.text = String.format("%02d:%02d", item.hour, item.minute)

            // default styles: if next -> primary; else muted
            if (item.isNext) {
                card.setBackgroundColor(ContextCompat.getColor(ctx, R.color.primary_color))
                tvName.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                tvTime.setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            } else {
                card.setBackgroundColor(ContextCompat.getColor(ctx, android.R.color.white))
                tvName.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                tvTime.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            }

            // bell tint: primary if enabled else muted
            val bellTint = if (item.alarmEnabled) R.color.primary_color else R.color.icon_gray
            ivBell.setColorFilter(ContextCompat.getColor(ctx, bellTint))
            ivBell.setOnClickListener { onBellClick(item, pos) }
        }
    }
}