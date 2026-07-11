package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.R
import com.resq.app.databinding.ItemTipDisasterBinding
import com.resq.app.ui.viewmodel.DisasterTipGroup

class TipDisasterGroupAdapter(
    private val onItemClick: (DisasterTipGroup) -> Unit
) : ListAdapter<DisasterTipGroup, TipDisasterGroupAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTipDisasterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTipDisasterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(group: DisasterTipGroup) {
            binding.tvTipDisasterName.text        = group.disasterName
            binding.tvTipDisasterDescription.text = itemView.context.resources.getQuantityString(
                R.plurals.tip_count,
                group.tipCount,
                group.tipCount
            )
            binding.ivTipDisasterIcon.setImageResource(getDisasterIcon(group.iconResName))
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

    private class DiffCallback : DiffUtil.ItemCallback<DisasterTipGroup>() {
        override fun areItemsTheSame(oldItem: DisasterTipGroup, newItem: DisasterTipGroup) =
            oldItem.disasterId == newItem.disasterId

        override fun areContentsTheSame(oldItem: DisasterTipGroup, newItem: DisasterTipGroup) =
            oldItem == newItem
    }
}