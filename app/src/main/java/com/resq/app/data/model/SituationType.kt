package com.resq.app.data.model

/**
 * Represents different situational contexts for disaster preparation.
 * Used to personalize preparation content based on user's location profile.
 */
enum class SituationType {
    /** User is located near a body of water (sea, river, lake) */
    NEAR_WATER,

    /** User is located in a mountainous area */
    MOUNTAINOUS,

    /** User is located in a landslide-prone area */
    LANDSLIDE_PRONE,

    /** User is located in a flood-prone area */
    FLOOD_PRONE,

    /** User is located near a volcano */
    NEAR_VOLCANO,

    /** User is located in an urban area */
    URBAN,

    /** User is located in a coastal area */
    COASTAL,

    /** General preparation applicable to all users */
    GENERAL
}