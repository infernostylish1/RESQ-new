package com.resq.app.util

import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.model.SituationType

/**
 * Stateless utility that maps a UserProfileEntity to situation types and
 * filters disaster lists. Created once here so HomeViewModel,
 * DisasterViewModel, and DisasterTipsViewModel all share identical logic.
 */
object ProfileSituationMapper {

    /**
     * Returns the list of SituationTypes that apply to this profile.
     * Always includes GENERAL. Order matches SituationType declaration order.
     */
    fun toSituationTypes(profile: UserProfileEntity): List<SituationType> {
        return buildList {
            add(SituationType.GENERAL)
            if (profile.isNearWater)          add(SituationType.NEAR_WATER)
            if (profile.isInMountainousArea)  add(SituationType.MOUNTAINOUS)
            if (profile.isInLandslideProne)   add(SituationType.LANDSLIDE_PRONE)
            if (profile.isInFloodProne)       add(SituationType.FLOOD_PRONE)
            if (profile.isNearVolcano)        add(SituationType.NEAR_VOLCANO)
            if (profile.isInUrbanArea)        add(SituationType.URBAN)
            if (profile.isInCoastalArea)      add(SituationType.COASTAL)
        }
    }

    /**
     * Filters a full disaster list down to those recommended for this profile.
     * A disaster is included if:
     *   - it is universal (applies to everyone), OR
     *   - at least one of its recommendation flags matches a true profile flag.
     *
     * Used by HomeViewModel and DisasterViewModel when a Flow from the DAO
     * is not available (e.g. when filtering an already-loaded in-memory list).
     */
    fun filterRecommended(
        disasters: List<DisasterEntity>,
        profile: UserProfileEntity
    ): List<DisasterEntity> {
        return disasters.filter { disaster ->
            disaster.isUniversal ||
                    (profile.isNearWater         && disaster.isRecommendedNearWater)  ||
                    (profile.isInMountainousArea && disaster.isRecommendedMountainous) ||
                    (profile.isInLandslideProne  && disaster.isRecommendedLandslide)  ||
                    (profile.isInFloodProne      && disaster.isRecommendedFlood)      ||
                    (profile.isNearVolcano       && disaster.isRecommendedVolcano)    ||
                    (profile.isInUrbanArea       && disaster.isRecommendedUrban)      ||
                    (profile.isInCoastalArea     && disaster.isRecommendedCoastal)
        }
    }

    /**
     * Inverse of filterRecommended — returns disasters NOT matched by the profile.
     * Used by DisasterViewModel to populate the "Other Disasters" section.
     */
    fun filterOther(
        disasters: List<DisasterEntity>,
        profile: UserProfileEntity
    ): List<DisasterEntity> {
        val recommended = filterRecommended(disasters, profile).map { it.id }.toSet()
        return disasters.filter { it.id !in recommended }
    }
}