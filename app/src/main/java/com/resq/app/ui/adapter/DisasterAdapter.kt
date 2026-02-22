package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.databinding.ItemDisasterBinding

class DisasterAdapter(
    private val onItemClick: (DisasterEntity) -> Unit
) : ListAdapter<DisasterEntity, DisasterAdapter.DisasterViewHolder>(DisasterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DisasterViewHolder {
        val binding = ItemDisasterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DisasterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DisasterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DisasterViewHolder(
        private val binding: ItemDisasterBinding
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
                tvDisasterName.text = disaster.name
                tvDisasterDescription.text = disaster.description

                val iconRes = getDisasterIcon(disaster.iconResName)
                ivDisasterIcon.setImageResource(iconRes)
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

    private class DisasterDiffCallback : DiffUtil.ItemCallback<DisasterEntity>() {
        override fun areItemsTheSame(oldItem: DisasterEntity, newItem: DisasterEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DisasterEntity, newItem: DisasterEntity): Boolean {
            return oldItem == newItem
        }
    }
}