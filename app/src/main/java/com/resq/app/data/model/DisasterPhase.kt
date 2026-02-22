package com.resq.app.data.model

/**
 * Represents the temporal phase of disaster preparation.
 * Used by DisasterDetailFragment to show phase-specific preparation steps.
 */
enum class DisasterPhase {
    /** Preparation steps to take before a disaster occurs */
    BEFORE,

    /** Actions to take during a disaster */
    DURING,

    /** Recovery steps after a disaster */
    AFTER
}