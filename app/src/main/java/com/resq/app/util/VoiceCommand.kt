package com.resq.app.util

sealed class VoiceCommand {

    // ── Navigation ────────────────────────────────────────────────────────────
    data object NavigateHome      : VoiceCommand()
    data object NavigateDisasters : VoiceCommand()
    data object NavigateFirstAid  : VoiceCommand()
    data object NavigateTips      : VoiceCommand()
    data object NavigateSettings  : VoiceCommand()
    data object NavigateContacts  : VoiceCommand()

    // ── Navigate to module screen with a specific tab (NEW) ───────────────────
    data class NavigateDisastersTab(val tab: String) : VoiceCommand()   // "recommended" | "all"
    data class NavigateTipsTab(val tab: String)      : VoiceCommand()   // "recommended" | "all"

    // ── Deep-link: disaster detail + phase ────────────────────────────────────
    data class OpenDisaster(val disasterName: String)                          : VoiceCommand()
    data class OpenDisasterDetail(val disasterId: Long, val disasterName: String) : VoiceCommand()

    data class OpenDisasterPhase(
        val disasterName: String,
        val phase: String
    ) : VoiceCommand()

    data class OpenDisasterPhaseResolved(
        val disasterId: Long,
        val disasterName: String,
        val phase: String
    ) : VoiceCommand()

    // ── Deep-link: first aid detail ───────────────────────────────────────────
    data class OpenFirstAidCategory(val categoryName: String)                       : VoiceCommand()
    data class OpenFirstAidDetail(val categoryId: Long, val categoryName: String)   : VoiceCommand()

    // ── Deep-link: tip detail ─────────────────────────────────────────────────
    data class OpenTipDetail(val disasterName: String)                              : VoiceCommand()
    data class OpenTipDetailResolved(val disasterId: Long, val disasterName: String): VoiceCommand()

    // ── Tab navigation inside detail screens ──────────────────────────────────
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

    // ── List commands ─────────────────────────────────────────────────────────
    data object ListAllDisasters : VoiceCommand()
    data object ListAllFirstAid  : VoiceCommand()
    data object ListAllTips      : VoiceCommand()

    // ── Emergency ────────────────────────────────────────────────────────────
    data object CallEmergency : VoiceCommand()
    data class  CallContact(val contactName: String) : VoiceCommand()

    // ── Unknown ───────────────────────────────────────────────────────────────
    data class Unknown(val text: String) : VoiceCommand()

    // ═════════════════════════════════════════════════════════════════════════
    //  Shared keyword tables (used by VoiceCommandManager)
    // ═════════════════════════════════════════════════════════════════════════

    companion object {

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

        val PHASE_KEYWORDS: Map<String, List<String>> = mapOf(
            "before" to listOf(
                "before", "prior", "preparation", "prepare", "preparedness",
                "before the", "prior to", "bago",
                "bago ang", "paghahanda", "paghanda", "ihanda",
                "bago pa", "bago mangyari",
                "bag-o", "bagong", "pag-handa"
            ),
            "during" to listOf(
                "during", "while", "when it happens", "at the time",
                "in the middle of", "ongoing",
                "habang", "sa panahon ng", "sa oras ng", "nang mangyari",
                "during the", "habang nangyayari", "ngayong",
                "habit ang", "ha bang", "sa panahon"
            ),
            "after" to listOf(
                "after", "following", "post", "recovery", "aftermath",
                "after the", "once it",
                "pagkatapos", "matapos", "pagkatapos ng", "matapos ang",
                "kapag tapos na", "recovery", "pagbangon",
                "pag-ka-tapos", "mata pos", "mag-ka-tapos"
            )
        )

        val TIP_TRIGGER_PHRASES: List<String> = listOf(
            "tips for", "tips on", "tips about", "tip for", "tip on",
            "what to do", "what should i do", "how to survive",
            "how to prepare", "how to handle", "guide for", "guide on",
            "advice for", "advice on", "safety tips", "survival tips",
            "mga tip", "mga tips", "payo para sa", "payo tungkol sa",
            "paano harapin", "paano ang", "paano kung", "ano ang dapat",
            "ano ang gagawin", "ano gagawin", "gagawin sa",
            "paano mag-ingat", "paano mag ingat", "kung paano",
            "mag tip", "mag tips", "pa yo", "payo",
            "pa a no", "paa no", "gawin sa", "dapat gawin"
        )

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
            ),
            "recommended" to listOf(
                "recommended", "suggestions", "suggested", "popular",
                "top picks", "featured", "best",
                "rekomendado", "inirerekomenda", "mungkahi", "iminumungkahi",
                "mga mungkahi", "pinakamaganda"
            ),
            "all" to listOf(
                "all", "show all", "view all", "everything", "complete list",
                "full list", "see all", "list all",
                "lahat", "ipakita lahat", "lahat ng", "buo", "kumpleto",
                "buong listahan", "mga lahat", "tignan lahat"
            )
        )

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

        val INTENT_PREFIXES: List<String> = listOf(
            "show me", "open", "go to", "navigate to", "take me to",
            "i want to see", "i want to know about", "tell me about",
            "what is", "what are", "how do i", "how do you", "how to",
            "search for", "find", "look for", "give me", "display",
            "show information about", "information about", "info about",
            "ipakita", "buksan", "pumunta sa", "dalhin ako sa",
            "gusto kong malaman", "ano ang", "paano ang", "sabihin mo",
            "hanapin", "ibigay sa akin", "ipakita sa akin",
            "i pak ita", "bu ksan", "pu munta"
        )
    }
}