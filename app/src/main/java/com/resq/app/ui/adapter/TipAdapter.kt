package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.databinding.ItemTipBinding

class TipAdapter(
    private val onFavoriteClick: (DisasterTipEntity) -> Unit,
    private val onShareClick: (DisasterTipEntity) -> Unit,
    private val onReadAloudClick: (DisasterTipEntity) -> Unit,
    // false in TipDetailFragment — the disaster name is already in the toolbar title,
    // so showing it again inside every card row is redundant.
    // Defaults to true so all existing call sites compile without changes.
    private val showCategory: Boolean = true
) : ListAdapter<TipAdapter.TipWithCategory, TipAdapter.TipViewHolder>(TipDiffCallback()) {

    data class TipWithCategory(
        val tip: DisasterTipEntity,
        val categoryName: String
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemTipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TipViewHolder(
        private val binding: ItemTipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onFavoriteClick(getItem(position).tip)
                }
            }

            binding.btnShare.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onShareClick(getItem(position).tip)
                }
            }

            binding.btnReadAloud.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onReadAloudClick(getItem(position).tip)
                }
            }
        }

        fun bind(item: TipWithCategory) {
            binding.apply {
                // Toggle visibility before setting text so the constraint
                // chain collapses and tv_tip_content fills the freed space
                tvTipCategory.visibility = if (showCategory) View.VISIBLE else View.GONE
                tvTipCategory.text       = item.categoryName

                tvTipContent.text = item.tip.tipText

                val favoriteIcon = if (item.tip.isFavorite) {
                    android.R.drawable.btn_star_big_on
                } else {
                    android.R.drawable.btn_star_big_off
                }
                btnFavorite.setImageResource(favoriteIcon)
            }
        }
    }

    private class TipDiffCallback : DiffUtil.ItemCallback<TipWithCategory>() {
        override fun areItemsTheSame(oldItem: TipWithCategory, newItem: TipWithCategory): Boolean {
            return oldItem.tip.id == newItem.tip.id
        }

        override fun areContentsTheSame(oldItem: TipWithCategory, newItem: TipWithCategory): Boolean {
            return oldItem == newItem
        }
    }
}