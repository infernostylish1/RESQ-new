// com/resq/app/ui/search/GlobalSearchAdapter.kt
package com.resq.app.ui.search

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.resq.app.R
import com.resq.app.databinding.ItemGlobalSearchResultBinding
import com.resq.app.util.SearchTextMatch

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_RESULT = 1

class GlobalSearchAdapter(
    private val onResultClick: (GlobalSearchResult) -> Unit
) : ListAdapter<GlobalSearchResult, RecyclerView.ViewHolder>(DiffCallback()) {

    private var highlightQuery = ""

    fun submitResults(results: List<GlobalSearchResult>, query: String) {
        val normalizedQuery = SearchTextMatch.normalizeQuery(query)
        val queryChanged = highlightQuery != normalizedQuery
        highlightQuery = normalizedQuery
        // Equal result objects still need fresh spans when only the query changes.
        // Do this before the async diff: a later submission can cancel a commit callback.
        if (queryChanged) notifyItemRangeChanged(0, itemCount)
        submitList(results)
    }

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
            binding.tvTitle.text = highlighted(result.title)
            binding.tvSubtitle.text = highlighted(result.subtitle)
            binding.root.setOnClickListener { onResultClick(result) }
        }

        private fun highlighted(text: String): CharSequence {
            val ranges = SearchTextMatch.ranges(text, highlightQuery)
            if (ranges.isEmpty()) return text
            val background = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorPrimary)
            val foreground = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimary)
            return SpannableString(text).apply {
                ranges.forEach { range ->
                    val end = range.last + 1
                    setSpan(BackgroundColorSpan(background), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(ForegroundColorSpan(foreground), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(StyleSpan(Typeface.BOLD), range.first, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
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
