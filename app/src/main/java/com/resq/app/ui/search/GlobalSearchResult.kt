// com/resq/app/ui/search/GlobalSearchResult.kt
package com.resq.app.ui.search

sealed class GlobalSearchResult {

    abstract val id: Long
    abstract val title: String
    abstract val subtitle: String
    abstract val iconResName: String
    abstract val relevanceScore: Int

    data class DisasterResult(
        override val id: Long,
        override val title: String,
        override val subtitle: String,
        override val iconResName: String,
        override val relevanceScore: Int
    ) : GlobalSearchResult()

    data class FirstAidResult(
        override val id: Long,
        override val title: String,
        override val subtitle: String,
        override val iconResName: String,
        override val relevanceScore: Int
    ) : GlobalSearchResult()

    data class TipResult(
        override val id: Long,
        val disasterId: Long,
        val disasterName: String,
        override val title: String,
        override val subtitle: String,
        override val iconResName: String,
        override val relevanceScore: Int
    ) : GlobalSearchResult()

    data class ContactResult(
        override val id: Long,
        override val title: String,
        override val subtitle: String,
        val phoneNumber: String,
        override val iconResName: String,
        override val relevanceScore: Int
    ) : GlobalSearchResult()

    // Section header — not a real result, used for grouping in RecyclerView
    data class SectionHeader(
        val label: String
    ) : GlobalSearchResult() {
        override val id: Long = -1L
        override val title: String = label
        override val subtitle: String = ""
        override val iconResName: String = ""
        override val relevanceScore: Int = Int.MAX_VALUE
    }
}