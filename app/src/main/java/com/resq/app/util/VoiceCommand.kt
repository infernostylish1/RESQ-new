package com.resq.app.util

sealed class VoiceCommand {

    // ── Navigation ────────────────────────────────────────────────────────────
    data object NavigateHome      : VoiceCommand()
    data object NavigateDisasters : VoiceCommand()
    data object NavigateFirstAid  : VoiceCommand()
    data object NavigateTips      : VoiceCommand()
    data object NavigateSettings  : VoiceCommand()
    data object NavigateContacts  : VoiceCommand()

    // ── Deep-link: disaster detail + phase ────────────────────────────────────
    data class OpenDisaster(val disasterName: String)                          : VoiceCommand()
    data class OpenDisasterDetail(val disasterId: Long, val disasterName: String) : VoiceCommand()

    /**
     * Navigate to a specific phase tab inside DisasterDetailFragment.
     * [phase] is one of: "before", "during", "after"
     */
    data class OpenDisasterPhase(
        val disasterName: String,
        val phase: String              // "before" | "during" | "after"
    ) : VoiceCommand()

    data class OpenDisasterPhaseResolved(
        val disasterId: Long,
        val disasterName: String,
        val phase: String              // "before" | "during" | "after"
    ) : VoiceCommand()

    // ── Deep-link: first aid detail ───────────────────────────────────────────
    data class OpenFirstAidCategory(val categoryName: String)                       : VoiceCommand()
    data class OpenFirstAidDetail(val categoryId: Long, val categoryName: String)   : VoiceCommand()

    // ── Deep-link: tip detail ─────────────────────────────────────────────────
    data class OpenTipDetail(val disasterName: String)                              : VoiceCommand()
    data class OpenTipDetailResolved(val disasterId: Long, val disasterName: String): VoiceCommand()

    // ── Tab navigation inside detail screens ──────────────────────────────────
    /**
     * Switch to a named tab while already inside a detail fragment.
     * [tabLabel] is a canonical English label: "before", "during", "after",
     * "steps", "symptoms", "treatment", etc.
     */
    data class SwitchTab(val tabLabel: String) : VoiceCommand()

    // ── Search ────────────────────────────────────────────────────────────────
    data class SearchDisaster(val query: String)  : VoiceCommand()
    data class SearchFirstAid(val query: String)  : VoiceCommand()
    data class GeneralSearch(val query: String)   : VoiceCommand()

    // ── App control ───────────────────────────────────────────────────────────
    data object GoBack      : VoiceCommand()
    data object ScrollUp    : VoiceCommand()
    data object ScrollDown  : VoiceCommand()

    // ── TTS ───────────────────────────────────────────────────────────────────
    data object ReadContent   : VoiceCommand()
    data object StopReading   : VoiceCommand()
    data object PauseReading  : VoiceCommand()
    data object ResumeReading : VoiceCommand()

    // ── Tips ──────────────────────────────────────────────────────────────────
    data object GetRandomTip : VoiceCommand()
    data object GetNextTip   : VoiceCommand()

    // ── Emergency ────────────────────────────────────────────────────────────
    data object CallEmergency : VoiceCommand()
    data class  CallContact(val contactName: String) : VoiceCommand()

    // ── Unknown ───────────────────────────────────────────────────────────────
    data class Unknown(val text: String) : VoiceCommand()

    // ═════════════════════════════════════════════════════════════════════════
    //  Shared keyword tables (used by VoiceCommandManager)
    // ═════════════════════════════════════════════════════════════════════════

    companion object {

        // ── Disaster keywords ─────────────────────────────────────────────────
        // Key = canonical name stored in DB
        val DISASTER_KEYWORDS: Map<String, List<String>> = mapOf(
            "Earthquake" to listOf(
                "earthquake", "quake", "seismic", "tremor", "lindol", "linog",
                "yanig", "pagyanig"
            ),
            "Flood" to listOf(
                "flood", "flooding", "baha", "pagbaha", "tubig-baha", "inundation"
            ),
            "Typhoon" to listOf(
                "typhoon", "storm", "cyclone", "hurricane", "bagyo", "unos",
                "bagyong", "tropical storm"
            ),
            "Volcanic Eruption" to listOf(
                "volcano", "volcanic", "eruption", "lava", "bulkan", "bulkang",
                "pagsabog", "ash fall", "ashfall", "lahar"
            ),
            "Landslide" to listOf(
                "landslide", "mudslide", "rockslide", "avalanche",
                "guho", "pagguho", "mud flow", "mudflow"
            ),
            "Tsunami" to listOf(
                "tsunami", "tidal wave", "seismic wave", "tsikamo",
                "alon", "malaking alon"
            ),
            "Fire" to listOf(
                "fire", "wildfire", "blaze", "sunog", "apoy", "pagsunog",
                "house fire", "building fire"
            ),
            "Drought" to listOf(
                "drought", "dry spell", "water shortage", "tagtuyot",
                "pagkaupos ng tubig", "kakulangan ng tubig"
            )
        )

        // ── Phase keywords ────────────────────────────────────────────────────
        // Maps raw spoken words → canonical phase name
        val PHASE_KEYWORDS: Map<String, List<String>> = mapOf(
            "before" to listOf(
                // English
                "before", "prior", "preparation", "prepare", "preparedness",
                "before the", "prior to", "bago",
                // Tagalog phonetic transcriptions (what STT returns)
                "bago ang", "paghahanda", "paghanda", "ihanda",
                "bago pa", "bago mangyari",
                // common mis-transcriptions
                "bag-o", "bagong", "pag-handa"
            ),
            "during" to listOf(
                // English
                "during", "while", "when it happens", "at the time",
                "in the middle of", "ongoing",
                // Tagalog
                "habang", "sa panahon ng", "sa oras ng", "nang mangyari",
                "during the", "habang nangyayari", "ngayong",
                // common mis-transcriptions
                "habit ang", "ha bang", "sa panahon"
            ),
            "after" to listOf(
                // English
                "after", "following", "post", "recovery", "aftermath",
                "after the", "once it",
                // Tagalog
                "pagkatapos", "matapos", "pagkatapos ng", "matapos ang",
                "kapag tapos na", "recovery", "pagbangon",
                // common mis-transcriptions
                "pag-ka-tapos", "mata pos", "mag-ka-tapos"
            )
        )

        // ── Tip trigger phrases ───────────────────────────────────────────────
        val TIP_TRIGGER_PHRASES: List<String> = listOf(
            // English
            "tips for", "tips on", "tips about", "tip for", "tip on",
            "what to do", "what should i do", "how to survive",
            "how to prepare", "how to handle", "guide for", "guide on",
            "advice for", "advice on", "safety tips", "survival tips",
            // Tagalog — exact as typed by STT
            "mga tip", "mga tips", "payo para sa", "payo tungkol sa",
            "paano harapin", "paano ang", "paano kung", "ano ang dapat",
            "ano ang gagawin", "ano gagawin", "gagawin sa",
            "paano mag-ingat", "paano mag ingat", "kung paano",
            // Phonetic Tagalog transcriptions that STT produces
            "mag tip", "mag tips", "pa yo", "payo",
            "pa a no", "paa no", "gawin sa", "dapat gawin"
        )

        // ── Tab labels for detail fragments ───────────────────────────────────
        // These map what the user says → the canonical tab label in the UI
        val TAB_KEYWORD_MAP: Map<String, List<String>> = mapOf(
            "before" to listOf(
                "before", "bago", "paghanda", "preparation", "prepare", "prior",
                "paghahanda", "ihanda", "bago ang", "before ang"
            ),
            "during" to listOf(
                "during", "habang", "sa panahon", "while", "ongoing",
                "habang nangyayari", "during the", "sa oras"
            ),
            "after" to listOf(
                "after", "pagkatapos", "matapos", "recovery", "aftermath",
                "post", "pagbangon", "after the", "kapag tapos"
            ),
            "steps" to listOf(
                "steps", "how to", "procedure", "instructions",
                "hakbang", "paraan", "proseso"
            ),
            "symptoms" to listOf(
                "symptoms", "signs", "sintomas", "palatandaan", "mga sintomas"
            ),
            "treatment" to listOf(
                "treatment", "remedy", "cure", "lunas", "gamot", "paggamot"
            ),
            "overview" to listOf(
                "overview", "info", "information", "about", "tungkol",
                "impormasyon", "pangkalahatang"
            )
        )

        // ── First aid keywords ────────────────────────────────────────────────
        val FIRST_AID_KEYWORDS: Map<String, List<String>> = mapOf(
            "CPR" to listOf(
                "cpr", "cardiopulmonary", "resuscitation", "heart attack",
                "cardiac", "puso", "atake sa puso", "cardiac arrest"
            ),
            "Bleeding" to listOf(
                "bleeding", "blood", "wound", "cut", "hemorrhage",
                "dugo", "sugat", "pagdurugo", "hematoma"
            ),
            "Burns" to listOf(
                "burn", "burns", "scald", "fire injury", "paso", "nasunog",
                "sunburn", "thermal"
            ),
            "Choking" to listOf(
                "choke", "choking", "heimlich", "airway", "suffocate",
                "inumin", "nailagan", "nasamid", "samid"
            ),
            "Fracture" to listOf(
                "fracture", "broken bone", "break", "sprain",
                "bali", "nabali", "broken", "buto"
            ),
            "Poisoning" to listOf(
                "poison", "poisoning", "toxic", "overdose", "ingestion",
                "lason", "nalason", "nakakalason"
            ),
            "Stroke" to listOf(
                "stroke", "brain attack", "paralysis",
                "atake sa utak", "paralisis", "brain stroke"
            ),
            "Drowning" to listOf(
                "drowning", "drown", "water rescue", "submerged",
                "nalunod", "lunod", "pagkalunod"
            )
        )

        // ── Intent prefixes (stripped before subject matching) ────────────────
        val INTENT_PREFIXES: List<String> = listOf(
            // English
            "show me", "open", "go to", "navigate to", "take me to",
            "i want to see", "i want to know about", "tell me about",
            "what is", "what are", "how do i", "how do you", "how to",
            "search for", "find", "look for", "give me", "display",
            "show information about", "information about", "info about",
            // Tagalog
            "ipakita", "buksan", "pumunta sa", "dalhin ako sa",
            "gusto kong malaman", "ano ang", "paano ang", "sabihin mo",
            "hanapin", "ibigay sa akin", "ipakita sa akin",
            // Phonetic transcriptions
            "i pak ita", "bu ksan", "pu munta"
        )
    }
}