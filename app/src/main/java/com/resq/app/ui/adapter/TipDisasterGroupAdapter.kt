package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
                if (position != RecyclerView.NO_ID.toInt()) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(group: DisasterTipGroup) {
            binding.tvTipDisasterName.text        = group.disasterName
            binding.tvTipDisasterDescription.text = itemView.context.resources.getQuantityString(
                com.resq.app.R.plurals.tip_count,
                group.tipCount,
                group.tipCount
            )
            val iconRes = getDisasterIcon(group.iconResName)
            binding.ivTipDisasterIcon.setImageResource(iconRes)
        }

        private fun getDisasterIcon(iconName: String): Int {
            return when (iconName.lowercase()) {
                "earthquake"        -> android.R.drawable.ic_dialog_alert
                "typhoon"           -> android.R.drawable.ic_menu_compass
                "flood"             -> android.R.drawable.ic_menu_directions
                "volcanic_eruption" -> android.R.drawable.ic_menu_mapmode
                "landslide"         -> android.R.drawable.ic_menu_gallery
                "tsunami"           -> android.R.drawable.ic_menu_directions
                "fire"              -> android.R.drawable.ic_menu_report_image
                else                -> android.R.drawable.ic_dialog_alert
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