package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
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
                // Step number, title, description — always shown
                tvStepNumber.text      = stepNumber.toString()
                tvStepTitle.text       = step.title
                tvStepDescription.text = step.content

                // Media placeholder — show only when a media resource name is present.
                // Replace tv_media_label text with the resource name so a developer
                // can easily identify which asset belongs here.
                val hasMedia = !step.mediaResName.isNullOrBlank()
                cardMedia.isVisible = hasMedia
                if (hasMedia) {
                    tvMediaLabel.text = step.mediaResName
                }

                // Warning card — show when sectionTitle signals a warning or
                // important notice. Uses sectionTitle as the displayed message
                // because FirstAidContentEntity has no separate tip/warning field.
                val sectionLower = step.sectionTitle.lowercase()
                val hasWarning   = sectionLower.contains("warning") ||
                        sectionLower.contains("important")
                cardWarning.isVisible = hasWarning
                if (hasWarning) {
                    tvWarning.text = step.sectionTitle
                }
            }
        }
    }

    private class StepDiffCallback : DiffUtil.ItemCallback<FirstAidContentEntity>() {
        override fun areItemsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ): Boolean = oldItem == newItem
    }
}