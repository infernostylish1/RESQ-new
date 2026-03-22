// com/resq/app/data/repository/GlobalSearchRepository.kt
package com.resq.app.data.repository

import com.resq.app.ui.search.GlobalSearchResult
import com.resq.app.util.SearchHelper
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
                GlobalSearchResult.DisasterResult(
                    id             = entity.id,
                    title          = entity.name,
                    subtitle       = entity.description
                        .take(80)
                        .let { if (entity.description.length > 80) "$it…" else it },
                    iconResName    = entity.iconResName,
                    relevanceScore = searchHelper.calculateRelevanceScore(
                        entity.name + " " + entity.description, normalized
                    )
                )
            }.sortedByDescending { it.relevanceScore }

            if (disasterResults.isNotEmpty()) {
                results += GlobalSearchResult.SectionHeader("Disasters")
                results += disasterResults
            }

            // ── First Aid ─────────────────────────────────────────────────────
            val firstAidResults = firstAidCategories.map { entity ->
                GlobalSearchResult.FirstAidResult(
                    id             = entity.id,
                    title          = entity.name,
                    subtitle       = entity.description
                        .take(80)
                        .let { if (entity.description.length > 80) "$it…" else it },
                    iconResName    = entity.iconResName,
                    relevanceScore = searchHelper.calculateRelevanceScore(
                        entity.name + " " + entity.description, normalized
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
                        searchHelper.calculateRelevanceScore(it.tipText, normalized)
                    }!!
                    GlobalSearchResult.TipResult(
                        id             = topTip.id,
                        disasterId     = disasterId,
                        disasterName   = "", // filled below if needed — kept simple
                        title          = topTip.tipText
                            .take(60)
                            .let { if (topTip.tipText.length > 60) "$it…" else it },
                        subtitle       = "Tip • Disaster #$disasterId",
                        iconResName    = "ic_tip",
                        relevanceScore = searchHelper.calculateRelevanceScore(
                            topTip.tipText, normalized
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
}