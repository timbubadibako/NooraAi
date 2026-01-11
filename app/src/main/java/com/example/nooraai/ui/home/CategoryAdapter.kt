package com.example.nooraai.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.nooraai.R
import com.example.nooraai.data.Category

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.VH>(DIFF) {

    private var selectedCategoryId: Int? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val btn: Button = view.findViewById(R.id.btnCategoryItem)
        fun bind(cat: Category) {
            btn.text = cat.name
            btn.isSelected = (cat.id == selectedCategoryId)
            // update visual selected state (use selector drawable for btn background)
            btn.setOnClickListener {
                val prev = selectedCategoryId
                selectedCategoryId = cat.id
                // refresh: notify prev + this so selection UI updates
                prev?.let { prevId ->
                    val prevIndex = currentList.indexOfFirst { it.id == prevId }
                    if (prevIndex >= 0) notifyItemChanged(prevIndex)
                }
                notifyItemChanged(bindingAdapterPosition)
                onClick(cat)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    fun setSelectedById(id: Int?) {
        if (id == selectedCategoryId) return
        val prev = selectedCategoryId
        selectedCategoryId = id
        if (prev != null) {
            val prevIndex = currentList.indexOfFirst { it.id == prev }
            if (prevIndex >= 0) notifyItemChanged(prevIndex)
        }
        if (id != null) {
            val newIndex = currentList.indexOfFirst { it.id == id }
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }
    }
}