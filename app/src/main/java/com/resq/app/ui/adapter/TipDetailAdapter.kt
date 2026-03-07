package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.databinding.ItemPreparationStepBinding

/**
 * Adapter for TipDetailFragment.
 * Binds [DisasterTipEntity] items into [ItemPreparationStepBinding].
 *
 * [DisasterTipEntity] has no title field — [tvStepTitle] is hidden so the full
 * tip text appears once in [tvStepDescription] only.
 *
 * [DisasterTipEntity] has no mediaResName field — [cardMedia] is always hidden.
 * The explicit hide is intentional: item_preparation_step.xml uses
 * tools:visibility="visible" on card_media for design-time preview, so we
 * must set GONE at runtime ourselves rather than relying on the XML default.
 *
 * [DisasterTipEntity] has no secondary tip field — [cardTip] is always hidden.
 */
class TipDetailAdapter(
    private var languageCode: String = "en"
) : ListAdapter<DisasterTipEntity, TipDetailAdapter.TipDetailViewHolder>(DIFF_CALLBACK) {

    fun updateLanguage(code: String) {
        languageCode = code
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipDetailViewHolder {
        val binding = ItemPreparationStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TipDetailViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipDetailViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1, languageCode)
    }

    class TipDetailViewHolder(
        private val binding: ItemPreparationStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tip: DisasterTipEntity, stepNumber: Int, languageCode: String) {
            val fullText = if (languageCode == "tl") tip.tipTextTagalog else tip.tipText

            binding.tvStepNumber.text = stepNumber.toString()

            // No title field on DisasterTipEntity — hide to avoid blank space.
            binding.tvStepTitle.visibility = View.GONE

            binding.tvStepDescription.text = fullText

            // No mediaResName field on DisasterTipEntity — always hide.
            binding.cardMedia.visibility = View.GONE

            // No secondary tip field on DisasterTipEntity — always hide.
            binding.cardTip.visibility = View.GONE
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DisasterTipEntity>() {
            override fun areItemsTheSame(
                oldItem: DisasterTipEntity,
                newItem: DisasterTipEntity
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: DisasterTipEntity,
                newItem: DisasterTipEntity
            ): Boolean = oldItem == newItem
        }
    }
}