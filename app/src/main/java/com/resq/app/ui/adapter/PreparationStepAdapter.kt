package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.databinding.ItemPreparationStepBinding
import com.resq.app.util.MediaLoader

class PreparationStepAdapter :
    ListAdapter<DisasterPreparationEntity, PreparationStepAdapter.StepViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemPreparationStepBinding.inflate(
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
        private val binding: ItemPreparationStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Holds the active ExoPlayer for VIDEO items.
         * Null for IMAGE, GIF, TEXT, or when no media is present.
         * Released in onViewRecycled() via releasePlayer().
         */
        private var player: ExoPlayer? = null

        fun bind(entity: DisasterPreparationEntity) {
            // Release any player from a previously bound VIDEO item before
            // binding new content. This covers rebind without recycle.
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

            // Body text — content is the body field on DisasterPreparationEntity.
            binding.tvStepDescription.text = entity.content

            // Tip card — tip is nullable on this entity.
            val tip = entity.tip
            if (!tip.isNullOrBlank()) {
                binding.cardTip.visibility = View.VISIBLE
                binding.tvTip.text = tip
            } else {
                binding.cardTip.visibility = View.GONE
            }
        }

        /** Stops and releases the ExoPlayer held by this ViewHolder. Safe to call when null. */
        fun releasePlayer() {
            MediaLoader.releasePlayer(player)
            player = null
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DisasterPreparationEntity>() {
        override fun areItemsTheSame(
            oldItem: DisasterPreparationEntity,
            newItem: DisasterPreparationEntity
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: DisasterPreparationEntity,
            newItem: DisasterPreparationEntity
        ) = oldItem == newItem
    }
}