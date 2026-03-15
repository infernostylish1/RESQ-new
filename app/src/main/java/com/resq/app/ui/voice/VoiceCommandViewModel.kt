package com.resq.app.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.FirstAidRepository
import com.resq.app.util.RecognitionState
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
    private val firstAidRepository: FirstAidRepository
) : ViewModel() {

    val recognitionState: StateFlow<RecognitionState> =
        voiceCommandManager.recognitionState

    private val _commandEvent = MutableSharedFlow<VoiceCommand>()
    val commandEvent: SharedFlow<VoiceCommand> = _commandEvent.asSharedFlow()

    fun initialize(context: Context) {
        voiceCommandManager.initialize(context)
    }

    fun startListening() {
        voiceCommandManager.startListening { rawCommand ->
            viewModelScope.launch {
                val resolved = resolveCommand(rawCommand)
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

            // NEW: resolve disaster name → id, keep phase info
            is VoiceCommand.OpenDisasterPhase -> {
                val entity = disasterRepository.getDisasterByName(command.disasterName)
                if (entity != null) {
                    VoiceCommand.OpenDisasterPhaseResolved(entity.id, entity.name, command.phase)
                } else {
                    // disaster not found — go to list, phase intent lost gracefully
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

    fun resetState() {
        voiceCommandManager.resetToIdle()
    }

    fun cancelListening() { voiceCommandManager.cancel() }
    fun stopListening()   { voiceCommandManager.stopListening() }

    override fun onCleared() {
        super.onCleared()
        voiceCommandManager.destroy()
    }
}