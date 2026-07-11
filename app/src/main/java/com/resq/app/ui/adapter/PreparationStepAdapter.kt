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

    // Track ViewHolders that currently have an active ExoPlayer
    private val activeHolders = mutableSetOf<StepViewHolder>()

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

    /**
     * Pause all currently active video players.
     * Call this when the fragment is paused (e.g., user navigates away, app goes to background).
     */
    fun pauseAllPlayers() {
        activeHolders.forEach { it.pausePlayer() }
    }

    /**
     * Resume all previously active video players.
     * Call this when the fragment is resumed.
     */
    fun resumeAllPlayers() {
        activeHolders.forEach { it.resumePlayer() }
    }

    /**
     * Stops and releases all active video players immediately.
     * Call this when the fragment is being destroyed (onDestroyView) to prevent memory leaks.
     */
    fun releaseAllPlayers() {
        activeHolders.forEach { it.releasePlayer() }
        activeHolders.clear()
    }

    inner class StepViewHolder(
        private val binding: ItemPreparationStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var player: ExoPlayer? = null

        fun bind(entity: DisasterPreparationEntity) {
            // Release any previous player
            releasePlayer()

            player = MediaLoader.bind(
                context      = binding.root.context,
                cardMedia    = binding.cardMedia,
                ivStepMedia  = binding.ivStepMedia,
                pvStepMedia  = binding.pvStepMedia,
                mediaResName = entity.mediaResName,
                mediaType    = entity.mediaType
            )

            // Track this holder if it has a video player
            if (player != null) {
                activeHolders.add(this)
            } else {
                activeHolders.remove(this)
            }

            binding.tvStepNumber.text = entity.displayOrder.toString()
            binding.tvStepTitle.text = entity.title
            binding.tvStepDescription.text = entity.content

            val tip = entity.tip
            if (!tip.isNullOrBlank()) {
                binding.cardTip.visibility = View.VISIBLE
                binding.tvTip.text = tip
            } else {
                binding.cardTip.visibility = View.GONE
            }
        }

        fun releasePlayer() {
            MediaLoader.releasePlayer(player)
            player = null
            activeHolders.remove(this)
        }

        fun pausePlayer() {
            player?.playWhenReady = false
        }

        fun resumePlayer() {
            player?.playWhenReady = true
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