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

                val iconRes = getCategoryIcon(item.category.iconResName)
                ivCategoryIcon.setImageResource(iconRes)
            }
        }

        private fun getCategoryIcon(iconName: String): Int {
            return when (iconName.lowercase()) {
                "cpr" -> android.R.drawable.ic_menu_help
                "choking" -> android.R.drawable.ic_menu_info_details
                "bleeding" -> android.R.drawable.ic_menu_add
                "burns" -> android.R.drawable.ic_menu_report_image
                "fractures" -> android.R.drawable.ic_menu_edit
                "drowning" -> android.R.drawable.ic_menu_directions
                "poisoning" -> android.R.drawable.ic_dialog_alert
                "heart_attack" -> android.R.drawable.ic_menu_myplaces
                "stroke" -> android.R.drawable.ic_menu_compass
                "seizure" -> android.R.drawable.ic_menu_view
                else -> android.R.drawable.ic_menu_help
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