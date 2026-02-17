package com.resq.app.data.model

/**
 * Represents the type of media content associated with information entries.
 */
enum class MediaType {
    /** Plain text content only */
    TEXT,

    /** Static image content */
    IMAGE,

    /** Animated GIF content */
    GIF,

    /** Preloaded video content */
    VIDEO,

    /** Audio content for TTS or preloaded audio */
    AUDIO
}