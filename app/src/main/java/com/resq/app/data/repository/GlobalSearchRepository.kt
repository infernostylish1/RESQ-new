// com/resq/app/data/repository/GlobalSearchRepository.kt
package com.resq.app.data.repository

import com.resq.app.ui.search.GlobalSearchResult
import com.resq.app.util.SearchHelper
import com.resq.app.util.SearchTextMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalSearchRepository @Inject constructor(
    private val disasterRepository: DisasterRepository,
    private val firstAidRepository: FirstAidRepository,
    private val disasterTipRepository: DisasterTipRepository,
    private val emergencyContactRepository: EmergencyContactRepository,
    private val searchHelper: SearchHelper
) {

    /**
     * Searches across all 4 data sources and emits a flat, scored,
     * section-headed list ready for the adapter.
     */
    fun search(query: String): Flow<List<GlobalSearchResult>> {
        val normalized = searchHelper.normalizeQuery(query)

        return combine(
            disasterRepository.searchDisasters(normalized),
            firstAidRepository.searchCategories(normalized),
            disasterTipRepository.searchTips(normalized),
            emergencyContactRepository.searchContacts(normalized)
        ) { disasters, firstAidCategories, tips, contacts ->

            val results = mutableListOf<GlobalSearchResult>()

            // ── Disasters ─────────────────────────────────────────────────────
            val disasterResults = disasters.map { entity ->
                val useTagalog = !entity.name.contains(normalized, ignoreCase = true) &&
                    entity.nameTagalog.contains(normalized, ignoreCase = true)
                val name = if (useTagalog) entity.nameTagalog else entity.name
                val description = if (useTagalog) entity.descriptionTagalog else entity.description
                GlobalSearchResult.DisasterResult(
                    id             = entity.id,
                    title          = name,
                    subtitle       = SearchTextMatch.snippet(description, normalized),
                    iconResName    = entity.iconResName,
                    relevanceScore = searchHelper.calculateRelevanceScore(
                        "$name $description", normalized
                    )
                )
            }.sortedByDescending { it.relevanceScore }

            if (disasterResults.isNotEmpty()) {
                results += GlobalSearchResult.SectionHeader("Disasters")
                results += disasterResults
            }

            // ── First Aid ─────────────────────────────────────────────────────
            val firstAidResults = firstAidCategories.map { entity ->
                val englishMatches = entity.name.contains(normalized, ignoreCase = true) ||
                    entity.description.contains(normalized, ignoreCase = true)
                val tagalogMatches = entity.nameTagalog.contains(normalized, ignoreCase = true) ||
                    entity.descriptionTagalog.contains(normalized, ignoreCase = true)
                val useTagalog = !englishMatches && tagalogMatches
                val name = if (useTagalog) entity.nameTagalog else entity.name
                val description = if (useTagalog) entity.descriptionTagalog else entity.description
                GlobalSearchResult.FirstAidResult(
                    id             = entity.id,
                    title          = name,
                    subtitle       = SearchTextMatch.snippet(description, normalized),
                    iconResName    = entity.iconResName,
                    relevanceScore = searchHelper.calculateRelevanceScore(
                        "$name $description", normalized
                    )
                )
            }.sortedByDescending { it.relevanceScore }

            if (firstAidResults.isNotEmpty()) {
                results += GlobalSearchResult.SectionHeader("First Aid")
                results += firstAidResults
            }

            // ── Tips ──────────────────────────────────────────────────────────
            // Tips belong to disasters — group by disaster, show top tip per group
            val tipResults = tips
                .groupBy { it.disasterId }
                .map { (disasterId, tipList) ->
                    val topTip = tipList.maxByOrNull {
                        searchHelper.calculateRelevanceScore(
                            matchingText(it.tipText, it.tipTextTagalog, normalized), normalized
                        )
                    }!!
                    val tipText = matchingText(topTip.tipText, topTip.tipTextTagalog, normalized)
                    GlobalSearchResult.TipResult(
                        id             = topTip.id,
                        disasterId     = disasterId,
                        disasterName   = "", // filled below if needed — kept simple
                        title          = SearchTextMatch.snippet(tipText, normalized),
                        subtitle       = "Tip • Disaster #$disasterId",
                        iconResName    = "ic_tip",
                        relevanceScore = searchHelper.calculateRelevanceScore(
                            tipText, normalized
                        )
                    )
                }.sortedByDescending { it.relevanceScore }

            if (tipResults.isNotEmpty()) {
                results += GlobalSearchResult.SectionHeader("Tips")
                results += tipResults
            }

            // ── Emergency Contacts ────────────────────────────────────────────
            val contactResults = contacts.map { entity ->
                GlobalSearchResult.ContactResult(
                    id             = entity.id,
                    title          = entity.name,
                    subtitle       = entity.phoneNumber +
                            (entity.description?.let { " · $it" } ?: ""),
                    phoneNumber    = entity.phoneNumber,
                    iconResName    = "ic_contact",
                    relevanceScore = searchHelper.calculateRelevanceScore(
                        entity.name + " " + (entity.description ?: ""), normalized
                    )
                )
            }.sortedByDescending { it.relevanceScore }

            if (contactResults.isNotEmpty()) {
                results += GlobalSearchResult.SectionHeader("Emergency Contacts")
                results += contactResults
            }

            results
        }
    }

    private fun matchingText(english: String, tagalog: String, query: String): String =
        if (!english.contains(query, ignoreCase = true) && tagalog.contains(query, ignoreCase = true)) {
            tagalog
        } else {
            english
        }
}
