package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.R
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
                ivIcon.setImageResource(getDisasterIcon(disaster.iconResName))
            }
        }

        /**
         * Hardcoded mapping of icon resource name to actual drawable resource ID.
         */
        private fun getDisasterIcon(iconName: String): Int {
            return when (iconName) {
                "ic_earthquake"    -> R.drawable.ic_earthquake
                "ic_typhoon"       -> R.drawable.ic_typhoon
                "ic_flood"         -> R.drawable.ic_flood
                "ic_landslide"     -> R.drawable.ic_landslide
                "ic_volcano"       -> R.drawable.ic_volcano
                "ic_tsunami"       -> R.drawable.ic_tsunami
                "ic_fire"          -> R.drawable.ic_fire
                "ic_storm_surge"   -> R.drawable.ic_storm_surge
                else               -> android.R.drawable.ic_dialog_alert  // fallback
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