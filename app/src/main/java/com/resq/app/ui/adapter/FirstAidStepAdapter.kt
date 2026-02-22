package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.databinding.ItemFirstAidStepBinding

class FirstAidStepAdapter : ListAdapter<FirstAidContentEntity, FirstAidStepAdapter.FirstAidStepViewHolder>(
    StepDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstAidStepViewHolder {
        val binding = ItemFirstAidStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FirstAidStepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FirstAidStepViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class FirstAidStepViewHolder(
        private val binding: ItemFirstAidStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: FirstAidContentEntity, stepNumber: Int) {
            binding.apply {
                tvStepNumber.text = stepNumber.toString()
                tvStepTitle.text = step.title
                tvStepDescription.text = step.content
            }
        }
    }

    private class StepDiffCallback : DiffUtil.ItemCallback<FirstAidContentEntity>() {
        override fun areItemsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}