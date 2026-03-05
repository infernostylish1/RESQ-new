package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.databinding.ItemSectionHeaderBinding

/**
 * Single-item adapter that renders one section header string.
 * Used with ConcatAdapter in DisastersFragment to label the
 * "For You" and "Other Disasters" sections without touching DisasterAdapter.
 */
class SectionHeaderAdapter(private var title: String) :
    RecyclerView.Adapter<SectionHeaderAdapter.HeaderViewHolder>() {

    private var isVisible = true

    override fun getItemCount(): Int = if (isVisible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemSectionHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(title)
    }

    /**
     * Show or hide this header. Call notifyDataSetChanged() after
     * changing visibility so ConcatAdapter recomputes positions.
     */
    fun setVisible(visible: Boolean) {
        if (isVisible != visible) {
            isVisible = visible
            notifyDataSetChanged()
        }
    }

    fun updateTitle(newTitle: String) {
        title = newTitle
        if (isVisible) notifyItemChanged(0)
    }

    inner class HeaderViewHolder(
        private val binding: ItemSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(text: String) {
            binding.tvSectionHeader.text = text
        }
    }
}