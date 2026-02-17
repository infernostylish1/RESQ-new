package com.resq.app.data.model

/**
 * Represents user preference for disaster tips display.
 */
enum class TipsPreference {
    /** Tips filtered based on user's disaster profile from onboarding */
    RECOMMENDED,

    /** Tips randomly selected from all disasters */
    RANDOM
}