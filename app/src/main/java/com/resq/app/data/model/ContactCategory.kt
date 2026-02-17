package com.resq.app.data.model

/**
 * Represents categories of emergency contacts.
 */
enum class ContactCategory {
    /** National emergency hotlines (e.g., 911, Red Cross) */
    NATIONAL,

    /** Local emergency services (e.g., local fire station, barangay) */
    LOCAL,

    /** Personal emergency contacts added by user */
    PERSONAL
}