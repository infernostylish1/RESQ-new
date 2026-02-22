package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.databinding.ItemQuickAccessBinding

class QuickAccessAdapter(
    private val onItemClick: (DisasterEntity) -> Unit
) : ListAdapter<DisasterEntity, QuickAccessAdapter.QuickAccessViewHolder>(QuickAccessDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickAccessViewHolder {
        val binding = ItemQuickAccessBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickAccessViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickAccessViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QuickAccessViewHolder(
        private val binding: ItemQuickAccessBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(disaster: DisasterEntity) {
            binding.apply {
                tvTitle.text = disaster.name

                val iconRes = getDisasterIcon(disaster.iconResName)
                ivIcon.setImageResource(iconRes)
            }
        }

        private fun getDisasterIcon(iconName: String): Int {
            return when (iconName.lowercase()) {
                "earthquake" -> android.R.drawable.ic_dialog_alert
                "typhoon" -> android.R.drawable.ic_menu_compass
                "flood" -> android.R.drawable.ic_menu_directions
                "volcanic_eruption" -> android.R.drawable.ic_menu_mapmode
                "landslide" -> android.R.drawable.ic_menu_gallery
                "tsunami" -> android.R.drawable.ic_menu_directions
                "fire" -> android.R.drawable.ic_menu_report_image
                else -> android.R.drawable.ic_dialog_alert
            }
        }
    }

    private class QuickAccessDiffCallback : DiffUtil.ItemCallback<DisasterEntity>() {
        override fun areItemsTheSame(oldItem: DisasterEntity, newItem: DisasterEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DisasterEntity, newItem: DisasterEntity): Boolean {
            return oldItem == newItem
        }
    }
}