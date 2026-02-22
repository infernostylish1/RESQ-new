package com.resq.app.util

/**
 * Sealed class representing all possible voice commands in RESQ.
 * Used by VoiceCommandManager to parse and execute user voice input.
 */
sealed class VoiceCommand {

    // Navigation commands
    data object NavigateHome : VoiceCommand()
    data object NavigateDisasters : VoiceCommand()
    data object NavigateFirstAid : VoiceCommand()
    data object NavigateTips : VoiceCommand()
    data object NavigateSettings : VoiceCommand()
    data object NavigateContacts : VoiceCommand()

    // Search commands
    data class SearchDisaster(val query: String) : VoiceCommand()
    data class SearchFirstAid(val query: String) : VoiceCommand()
    data class GeneralSearch(val query: String) : VoiceCommand()

    // Specific disaster commands
    data class OpenDisaster(val disasterName: String) : VoiceCommand()
    data class OpenFirstAidCategory(val categoryName: String) : VoiceCommand()

    // Emergency contact commands
    data class CallContact(val contactName: String) : VoiceCommand()
    data object CallEmergency : VoiceCommand()

    // Tip commands
    data object GetRandomTip : VoiceCommand()
    data object GetNextTip : VoiceCommand()

    // TTS commands
    data object ReadContent : VoiceCommand()
    data object StopReading : VoiceCommand()
    data object PauseReading : VoiceCommand()
    data object ResumeReading : VoiceCommand()

    // App control
    data object GoBack : VoiceCommand()
    data object ScrollUp : VoiceCommand()
    data object ScrollDown : VoiceCommand()

    // Unknown command
    data class Unknown(val rawInput: String) : VoiceCommand()

    companion object {
        // Disaster keywords for matching
        val DISASTER_KEYWORDS = mapOf(
            "earthquake" to listOf("earthquake", "lindol", "quake", "seismic"),
            "typhoon" to listOf("typhoon", "bagyo", "storm", "cyclone"),
            "flood" to listOf("flood", "baha", "flooding", "flash flood"),
            "fire" to listOf("fire", "sunog", "blaze", "burning"),
            "volcanic" to listOf("volcano", "volcanic", "bulkan", "eruption", "lava"),
            "tsunami" to listOf("tsunami", "tidal wave", "daluyong"),
            "landslide" to listOf("landslide", "guho", "mudslide", "rockslide"),
            "storm surge" to listOf("storm surge", "daluyong ng bagyo", "surge")
        )

        // First aid keywords for matching
        val FIRST_AID_KEYWORDS = mapOf(
            "cpr" to listOf("cpr", "choking", "cardiac", "heart attack", "resuscitation", "heimlich"),
            "wounds" to listOf("wound", "bleeding", "cut", "laceration", "sugat", "dugo"),
            "burns" to listOf("burn", "scald", "paso", "thermal"),
            "fractures" to listOf("fracture", "broken bone", "sprain", "bali", "pilay"),
            "heat" to listOf("heat stroke", "hypothermia", "cold emergency", "heatstroke", "frostbite"),
            "drowning" to listOf("drowning", "drown", "nalunod", "water rescue")
        )
    }
}

/**
 * Result of voice command execution
 */
sealed class VoiceCommandResult {
    data class Success(val message: String) : VoiceCommandResult()
    data class Error(val message: String) : VoiceCommandResult()
    data class NavigationRequired(val destination: String) : VoiceCommandResult()
    data class ActionRequired(val action: String, val data: Any? = null) : VoiceCommandResult()
}