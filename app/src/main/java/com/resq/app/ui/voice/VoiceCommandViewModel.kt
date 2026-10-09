package com.resq.app.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.FirstAidRepository
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.util.RecognitionState
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.VoiceCommand
import com.resq.app.util.VoiceCommandManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceCommandViewModel @Inject constructor(
    private val voiceCommandManager: VoiceCommandManager,
    private val disasterRepository: DisasterRepository,
    private val firstAidRepository: FirstAidRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    val recognitionState: StateFlow<RecognitionState> =
        voiceCommandManager.recognitionState

    private val _commandEvent = MutableSharedFlow<VoiceCommand>()
    val commandEvent: SharedFlow<VoiceCommand> = _commandEvent.asSharedFlow()

    fun initialize(context: Context) {
        voiceCommandManager.initialize(context)
        ttsManager.initialize(context)

        viewModelScope.launch {
            val settings = userSettingsRepository.getSettingsOnce()
            settings?.let {
                // NOTE: voiceCommandManager.setLanguage(...) is a no-op now — the
                // Omnilingual ASR model is multilingual. Kept for symmetry.
                voiceCommandManager.setLanguage(it.preferredLanguage)
                ttsManager.setLanguage(it.preferredLanguage)
                ttsManager.setSpeechRate(it.ttsSpeed)
                ttsManager.setPitch(it.ttsPitch)
            }
        }
    }

    fun startListening() {
        voiceCommandManager.startListening { rawCommand ->
            viewModelScope.launch {
                val resolved = resolveCommand(rawCommand)
                provideVoiceFeedback(resolved)
                _commandEvent.emit(resolved)
            }
        }
    }

    private suspend fun resolveCommand(command: VoiceCommand): VoiceCommand {
        return when (command) {

            is VoiceCommand.OpenDisaster -> {
                val entity = disasterRepository.getDisasterByName(command.disasterName)
                if (entity != null) {
                    VoiceCommand.OpenDisasterDetail(entity.id, entity.name)
                } else {
                    VoiceCommand.NavigateDisasters
                }
            }

            is VoiceCommand.OpenDisasterPhase -> {
                val entity = disasterRepository.getDisasterByName(command.disasterName)
                if (entity != null) {
                    VoiceCommand.OpenDisasterPhaseResolved(entity.id, entity.name, command.phase)
                } else {
                    VoiceCommand.NavigateDisasters
                }
            }

            is VoiceCommand.OpenFirstAidCategory -> {
                val entity = firstAidRepository.getCategoryByName(command.categoryName)
                if (entity != null) {
                    VoiceCommand.OpenFirstAidDetail(entity.id, entity.name)
                } else {
                    VoiceCommand.NavigateFirstAid
                }
            }

            is VoiceCommand.OpenTipDetail -> {
                val entity = disasterRepository.getDisasterByName(command.disasterName)
                if (entity != null) {
                    VoiceCommand.OpenTipDetailResolved(entity.id, entity.name)
                } else {
                    VoiceCommand.NavigateTips
                }
            }

            else -> command
        }
    }

    private fun provideVoiceFeedback(command: VoiceCommand) {
        viewModelScope.launch {
            val settings = userSettingsRepository.getSettingsOnce()
            if (settings?.isTtsEnabled != true) return@launch

            val isTagalog = settings.preferredLanguage == "tl" || settings.preferredLanguage == "fil"

            val message = if (isTagalog) {
                getTagalogFeedback(command)
            } else {
                getEnglishFeedback(command)
            }

            message?.let { ttsManager.speak(it) }
        }
    }

    private fun getEnglishFeedback(command: VoiceCommand): String? = when (command) {
        is VoiceCommand.NavigateHome -> "Going home."
        is VoiceCommand.NavigateDisasters -> "Opening disasters list."
        is VoiceCommand.NavigateFirstAid -> "Opening first aid guides."
        is VoiceCommand.NavigateTips -> "Opening safety tips."
        is VoiceCommand.NavigateSettings -> "Opening settings."
        is VoiceCommand.NavigateContacts -> "Opening emergency contacts."
        is VoiceCommand.OpenDisasterDetail -> "Showing details for ${command.disasterName}."
        is VoiceCommand.OpenDisasterPhaseResolved -> "Showing ${command.phase} instructions for ${command.disasterName}."
        is VoiceCommand.OpenFirstAidDetail -> "Showing first aid for ${command.categoryName}."
        is VoiceCommand.OpenTipDetailResolved -> "Showing tips for ${command.disasterName}."
        is VoiceCommand.ReadContent -> "Reading content aloud."
        is VoiceCommand.StopReading -> "Stopping."
        is VoiceCommand.PauseReading -> "Paused."
        is VoiceCommand.ResumeReading -> "Resuming."
        is VoiceCommand.GetRandomTip -> "Here is a safety tip."
        is VoiceCommand.CallEmergency -> "Calling emergency services."
        is VoiceCommand.CallContact -> "Calling ${command.contactName}."
        is VoiceCommand.Unknown -> "I didn't quite catch that. Could you repeat?"
        else -> null
    }

    private fun getTagalogFeedback(command: VoiceCommand): String? = when (command) {
        is VoiceCommand.NavigateHome -> "Pumupunta sa home."
        is VoiceCommand.NavigateDisasters -> "Binubuksan ang listahan ng mga sakuna."
        is VoiceCommand.NavigateFirstAid -> "Binubuksan ang mga gabay sa first aid."
        is VoiceCommand.NavigateTips -> "Binubuksan ang mga tip sa kaligtasan."
        is VoiceCommand.NavigateSettings -> "Binubuksan ang mga setting."
        is VoiceCommand.NavigateContacts -> "Binubuksan ang mga emergency contact."
        is VoiceCommand.OpenDisasterDetail -> "Ipinapakita ang detalye para sa ${command.disasterName}."
        is VoiceCommand.OpenDisasterPhaseResolved -> "Ipinapakita ang mga tagubilin para sa ${command.disasterName}."
        is VoiceCommand.OpenFirstAidDetail -> "Ipinapakita ang first aid para sa ${command.categoryName}."
        is VoiceCommand.OpenTipDetailResolved -> "Ipinapakita ang mga tip para sa ${command.disasterName}."
        is VoiceCommand.ReadContent -> "Binabasa ang nilalaman."
        is VoiceCommand.StopReading -> "Humihinto."
        is VoiceCommand.PauseReading -> "Naka-pause."
        is VoiceCommand.ResumeReading -> "Ipinagpapatuloy."
        is VoiceCommand.GetRandomTip -> "Narito ang isang tip para sa kaligtasan."
        is VoiceCommand.CallEmergency -> "Tinatawag ang emergency services."
        is VoiceCommand.CallContact -> "Tinatawag si ${command.contactName}."
        is VoiceCommand.Unknown -> "Paumanhin, hindi ko naintindihan. Maaari mo bang ulitin?"
        else -> null
    }

    fun resetState() {
        voiceCommandManager.resetToIdle()
    }

    fun cancelListening() { voiceCommandManager.cancel() }
    fun stopListening()   {
        voiceCommandManager.stopListening()
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        voiceCommandManager.destroy()
    }
}