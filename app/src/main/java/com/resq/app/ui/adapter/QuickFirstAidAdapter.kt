package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.R
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.databinding.ItemQuickAccessBinding

class QuickFirstAidAdapter(
    private val onItemClick: (FirstAidCategoryEntity) -> Unit
) : ListAdapter<FirstAidCategoryEntity, QuickFirstAidAdapter.QuickFirstAidViewHolder>(
    CategoryDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickFirstAidViewHolder {
        val binding = ItemQuickAccessBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickFirstAidViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickFirstAidViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QuickFirstAidViewHolder(
        private val binding: ItemQuickAccessBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.ivIcon.contentDescription =
                binding.root.context.getString(R.string.cd_firstaid_icon)
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(category: FirstAidCategoryEntity) {
            binding.tvTitle.text = category.name
            binding.ivIcon.setImageResource(
                when (category.iconResName) {
                    "ic_cpr" -> R.drawable.ic_cpr
                    "ic_wound" -> R.drawable.ic_wound
                    "ic_burn" -> R.drawable.ic_burn
                    "ic_fracture" -> R.drawable.ic_fracture
                    "ic_temperature" -> R.drawable.ic_temperature
                    "ic_drowning" -> R.drawable.ic_drowning
                    else -> android.R.drawable.ic_dialog_alert
                }
            )
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<FirstAidCategoryEntity>() {
        override fun areItemsTheSame(
            oldItem: FirstAidCategoryEntity,
            newItem: FirstAidCategoryEntity
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: FirstAidCategoryEntity,
            newItem: FirstAidCategoryEntity
        ): Boolean = oldItem == newItem
    }
}
