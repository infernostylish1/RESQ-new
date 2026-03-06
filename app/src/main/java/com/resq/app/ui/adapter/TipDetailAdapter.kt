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
 * [DisasterTipEntity] has no separate title field — only tipText/tipTextTagalog.
 * [tvStepTitle] is hidden so the full tip text is shown once in [tvStepDescription] only,
 * matching the same pattern used by [PreparationStepAdapter] where title != content.
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

            // DisasterTipEntity has no title field — hide the title view entirely
            // so the tip text is not duplicated across tvStepTitle and tvStepDescription.
            binding.tvStepTitle.visibility = View.GONE

            binding.tvStepDescription.text = fullText

            // DisasterTipEntity has no secondary tip field — keep card hidden.
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