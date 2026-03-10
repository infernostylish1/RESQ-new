package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.databinding.ItemFirstAidStepBinding
import com.resq.app.util.MediaLoader

class FirstAidStepAdapter :
    ListAdapter<FirstAidContentEntity, FirstAidStepAdapter.StepViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemFirstAidStepBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: StepViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    inner class StepViewHolder(
        private val binding: ItemFirstAidStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Holds the active ExoPlayer for VIDEO items.
         * Null for IMAGE, GIF, TEXT, or when no media is present.
         * Released in onViewRecycled() via releasePlayer().
         */
        private var player: ExoPlayer? = null

        fun bind(entity: FirstAidContentEntity) {
            // Release any player from a previously bound VIDEO item before
            // binding new content. This covers rebind without recycle (e.g.
            // submitList on same ViewHolder position).
            releasePlayer()

            // Media card — MediaLoader owns all visibility decisions.
            // Returns a non-null ExoPlayer only when mediaType is VIDEO.
            player = MediaLoader.bind(
                context      = binding.root.context,
                cardMedia    = binding.cardMedia,
                ivStepMedia  = binding.ivStepMedia,
                pvStepMedia  = binding.pvStepMedia,
                mediaResName = entity.mediaResName,
                mediaType    = entity.mediaType
            )

            // Step number — displayOrder is the ordering field on this entity.
            binding.tvStepNumber.text = entity.displayOrder.toString()

            // Title
            binding.tvStepTitle.text = entity.title

            // Body text — content is the body field on FirstAidContentEntity.
            binding.tvStepDescription.text = entity.content

            // Warning card — driven by sectionTitle.
            // sectionTitle is non-null on this entity; no ?. needed.
            val sectionLower = entity.sectionTitle.lowercase()
            if (sectionLower.contains("warning") || sectionLower.contains("important")) {
                binding.cardWarning.visibility = View.VISIBLE
                binding.tvWarning.text = entity.sectionTitle
            } else {
                binding.cardWarning.visibility = View.GONE
            }
        }

        /** Stops and releases the ExoPlayer held by this ViewHolder. Safe to call when null. */
        fun releasePlayer() {
            MediaLoader.releasePlayer(player)
            player = null
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FirstAidContentEntity>() {
        override fun areItemsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: FirstAidContentEntity,
            newItem: FirstAidContentEntity
        ) = oldItem == newItem
    }
}