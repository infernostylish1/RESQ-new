package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.databinding.ItemPreparationStepBinding

class PreparationStepAdapter : ListAdapter<DisasterPreparationEntity, PreparationStepAdapter.PreparationStepViewHolder>(
    PreparationDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreparationStepViewHolder {
        val binding = ItemPreparationStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PreparationStepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PreparationStepViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class PreparationStepViewHolder(
        private val binding: ItemPreparationStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(preparation: DisasterPreparationEntity, stepNumber: Int) {
            binding.apply {
                tvStepNumber.text = stepNumber.toString()
                tvStepTitle.text = preparation.title
                tvStepDescription.text = preparation.content

                if (!preparation.tip.isNullOrBlank()) {
                    cardTip.visibility = View.VISIBLE
                    tvTip.text = preparation.tip
                } else {
                    cardTip.visibility = View.GONE
                }
            }
        }
    }

    private class PreparationDiffCallback : DiffUtil.ItemCallback<DisasterPreparationEntity>() {
        override fun areItemsTheSame(
            oldItem: DisasterPreparationEntity,
            newItem: DisasterPreparationEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: DisasterPreparationEntity,
            newItem: DisasterPreparationEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}