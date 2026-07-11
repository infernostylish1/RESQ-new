package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.R
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.databinding.ItemFirstAidCategoryBinding

class FirstAidCategoryAdapter(
    private val onItemClick: (FirstAidCategoryEntity) -> Unit
) : ListAdapter<FirstAidCategoryAdapter.CategoryWithStepCount, FirstAidCategoryAdapter.FirstAidCategoryViewHolder>(
    CategoryDiffCallback()
) {

    data class CategoryWithStepCount(
        val category: FirstAidCategoryEntity,
        val stepCount: Int
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstAidCategoryViewHolder {
        val binding = ItemFirstAidCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FirstAidCategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FirstAidCategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FirstAidCategoryViewHolder(
        private val binding: ItemFirstAidCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position).category)
                }
            }
        }

        fun bind(item: CategoryWithStepCount) {
            binding.apply {
                tvCategoryName.text = item.category.name
                tvStepCount.text = root.context.resources.getQuantityString(
                    R.plurals.step_count,
                    item.stepCount,
                    item.stepCount
                )

                ivCategoryIcon.setImageResource(getCategoryIcon(item.category.iconResName))
            }
        }

        /**
         * Hardcoded mapping of icon resource name to actual drawable resource ID.
         */
        private fun getCategoryIcon(iconName: String): Int {
            return when (iconName) {
                "ic_cpr"         -> R.drawable.ic_cpr
                "ic_wound"       -> R.drawable.ic_wound
                "ic_burn"        -> R.drawable.ic_burn
                "ic_fracture"    -> R.drawable.ic_fracture
                "ic_temperature" -> R.drawable.ic_temperature
                "ic_drowning"    -> R.drawable.ic_drowning
                else             -> android.R.drawable.ic_dialog_alert  // fallback
            }
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryWithStepCount>() {
        override fun areItemsTheSame(
            oldItem: CategoryWithStepCount,
            newItem: CategoryWithStepCount
        ): Boolean {
            return oldItem.category.id == newItem.category.id
        }

        override fun areContentsTheSame(
            oldItem: CategoryWithStepCount,
            newItem: CategoryWithStepCount
        ): Boolean {
            return oldItem == newItem
        }
    }
}