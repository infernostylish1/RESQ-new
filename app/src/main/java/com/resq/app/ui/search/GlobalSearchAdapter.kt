// com/resq/app/ui/search/GlobalSearchAdapter.kt
package com.resq.app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.resq.app.R
import com.resq.app.databinding.ItemGlobalSearchResultBinding

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_RESULT = 1

class GlobalSearchAdapter(
    private val onResultClick: (GlobalSearchResult) -> Unit
) : ListAdapter<GlobalSearchResult, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is GlobalSearchResult.SectionHeader) VIEW_TYPE_HEADER
        else VIEW_TYPE_RESULT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = inflater.inflate(
                R.layout.item_global_search_section_header, parent, false
            )
            HeaderViewHolder(view as TextView)
        } else {
            ResultViewHolder(
                ItemGlobalSearchResultBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GlobalSearchResult.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            else                                -> (holder as ResultViewHolder).bind(item)
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class HeaderViewHolder(private val textView: TextView) :
        RecyclerView.ViewHolder(textView) {

        fun bind(header: GlobalSearchResult.SectionHeader) {
            textView.text = header.label
        }
    }

    inner class ResultViewHolder(
        private val binding: ItemGlobalSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: GlobalSearchResult) {
            binding.tvTitle.text    = result.title
            binding.tvSubtitle.text = result.subtitle

            val ctx = binding.root.context

            // Configure category chip appearance
            val (chipText, chipColor) = when (result) {
                is GlobalSearchResult.DisasterResult ->
                    ctx.getString(R.string.nav_disasters) to
                            ctx.getColor(R.color.primary)
                is GlobalSearchResult.FirstAidResult ->
                    ctx.getString(R.string.nav_first_aid) to
                            ctx.getColor(R.color.firstaid_cpr)
                is GlobalSearchResult.TipResult ->
                    ctx.getString(R.string.nav_tips) to
                            ctx.getColor(R.color.secondary)
                is GlobalSearchResult.ContactResult ->
                    ctx.getString(R.string.nav_contacts) to
                            ctx.getColor(R.color.tertiary)
                is GlobalSearchResult.SectionHeader -> "" to 0
            }

            binding.chipCategory.text = chipText
            binding.chipCategory.chipBackgroundColor =
                android.content.res.ColorStateList.valueOf(chipColor)
            binding.chipCategory.setTextColor(ctx.getColor(R.color.on_primary))

            binding.root.setOnClickListener { onResultClick(result) }
        }
    }

    // ── DiffCallback ──────────────────────────────────────────────────────────

    private class DiffCallback : DiffUtil.ItemCallback<GlobalSearchResult>() {

        override fun areItemsTheSame(
            oldItem: GlobalSearchResult,
            newItem: GlobalSearchResult
        ): Boolean {
            if (oldItem::class != newItem::class) return false
            return when (oldItem) {
                is GlobalSearchResult.SectionHeader ->
                    oldItem.label == (newItem as GlobalSearchResult.SectionHeader).label
                else -> oldItem.id == newItem.id
            }
        }

        override fun areContentsTheSame(
            oldItem: GlobalSearchResult,
            newItem: GlobalSearchResult
        ): Boolean = oldItem == newItem
    }
}