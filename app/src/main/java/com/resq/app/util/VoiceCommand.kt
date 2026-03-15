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

    // Specific disaster commands — name only (list-level, triggers DB lookup in ViewModel)
    data class OpenDisaster(val disasterName: String) : VoiceCommand()
    data class OpenFirstAidCategory(val categoryName: String) : VoiceCommand()

    // Deep-link commands — resolved by VoiceCommandViewModel after DB lookup
    // These carry the real id + display name and are consumed directly by MainActivity
    data class OpenDisasterDetail(
        val disasterId: Long,
        val disasterName: String
    ) : VoiceCommand()

    data class OpenFirstAidDetail(
        val categoryId: Long,
        val categoryName: String
    ) : VoiceCommand()

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
        /**
         * Keys must match DisasterEntity.name exactly (Title Case) so the
         * DB lookup via getAllDisastersOnce() can find them with name.equals(key, ignoreCase=true).
         *
         * Value lists are the spoken keywords that map to each disaster.
         * Tagalog equivalents are included so Filipino users are covered.
         */
        val DISASTER_KEYWORDS = mapOf(
            "Earthquake" to listOf(
                "earthquake", "lindol", "quake", "seismic", "tremor",
                "ground shaking", "pagyanig", "yumanig"
            ),
            "Typhoon" to listOf(
                "typhoon", "bagyo", "storm", "cyclone", "hurricane",
                "tropical storm", "signal", "bagyong"
            ),
            "Flood" to listOf(
                "flood", "baha", "flooding", "flash flood", "tubig baha",
                "pag-apaw", "bumabaha"
            ),
            "Landslide" to listOf(
                "landslide", "guho", "mudslide", "rockslide",
                "pagguho", "pagguho ng lupa", "mud flow"
            ),
            "Volcanic Eruption" to listOf(
                "volcano", "volcanic", "bulkan", "eruption", "lava",
                "ashfall", "abo ng bulkan", "pagsabog", "bulkaniko",
                "volcanic eruption", "erupting"
            ),
            "Tsunami" to listOf(
                "tsunami", "tidal wave", "daluyong", "malaking alon",
                "ocean wave", "seismic wave"
            ),
            "Fire" to listOf(
                "fire", "sunog", "blaze", "burning", "nasusunog",
                "apoy", "nagliliyab", "house fire"
            ),
            "Storm Surge" to listOf(
                "storm surge", "daluyong ng bagyo", "surge",
                "coastal flooding", "storm wave", "tidal surge"
            )
        )

        /**
         * Keys must match FirstAidCategoryEntity.name exactly (Title Case).
         */
        val FIRST_AID_KEYWORDS = mapOf(
            "CPR & Choking" to listOf(
                "cpr", "choking", "cardiac", "heart attack", "resuscitation",
                "heimlich", "cardiac arrest", "heart stopped", "chest compression",
                "nabubulungan", "bulungan", "walang tibok", "pagkabulunan"
            ),
            "Wounds & Bleeding" to listOf(
                "wound", "bleeding", "cut", "laceration", "sugat", "dugo",
                "nagdurugo", "hiwa", "gash", "open wound", "blood"
            ),
            "Burns" to listOf(
                "burn", "scald", "paso", "thermal", "nasunog na balat",
                "nasunog", "sinunog", "hot water burn", "fire burn"
            ),
            "Fractures & Sprains" to listOf(
                "fracture", "broken bone", "sprain", "bali", "pilay",
                "nabali", "napilay", "broken arm", "broken leg", "joint"
            ),
            "Heat & Cold Emergencies" to listOf(
                "heat stroke", "hypothermia", "cold emergency", "heatstroke",
                "frostbite", "heat exhaustion", "overheating", "nalasing sa init",
                "sobrang init", "sobrang lamig", "heat", "cold emergency"
            ),
            "Drowning" to listOf(
                "drowning", "drown", "nalunod", "water rescue", "underwater",
                "hindi makahinga sa tubig", "water emergency", "lunod"
            )
        )

        /**
         * Question/intent prefixes that signal the user wants info or help
         * about something — used to strip the intent and extract the subject.
         * e.g. "what is an earthquake" → "earthquake"
         *      "what to do during flood" → "flood"
         *      "paano ang lindol" → "lindol"
         */
        val INTENT_PREFIXES = listOf(
            // English informational
            "what is", "what are", "what causes", "tell me about",
            "show me", "open", "explain", "info about", "information about",
            "learn about", "i want to know about",
            // English action/procedural
            "what to do", "what should i do", "how to", "how do i",
            "steps for", "guide for", "help with", "i need help with",
            "what happens during", "how to survive",
            // Tagalog informational
            "ano ang", "ano ba ang", "paano ang", "paano ba ang",
            "ipakita", "buksan", "alamin ang", "impormasyon tungkol sa",
            "kwentuhan mo ako tungkol sa",
            // Tagalog action/procedural
            "paano kung", "ano ang gagawin", "ano gagawin", "tulong sa",
            "paano harapin", "paano malalaman", "gabay sa",
            // Articles/fillers to strip after prefix removal
            "an", "a", "the", "ang", "yung", "mga", "about"
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