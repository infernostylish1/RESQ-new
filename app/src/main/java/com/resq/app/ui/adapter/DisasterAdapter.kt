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

                // Load the custom icon using the resource name from the entity
                val iconResId = getDisasterIconResId(disaster.iconResName)
                if (iconResId != 0) {
                    ivDisasterIcon.setImageResource(iconResId)
                } else {
                    // Fallback to default icon if resource not found
                    ivDisasterIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                }
            }
        }

        /**
         * Resolves the drawable resource ID from the icon resource name string.
         * Returns 0 if not found.
         */
        private fun getDisasterIconResId(iconName: String): Int {
            return try {
                itemView.context.resources.getIdentifier(
                    iconName,
                    "drawable",
                    itemView.context.packageName
                )
            } catch (e: Exception) {
                0
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