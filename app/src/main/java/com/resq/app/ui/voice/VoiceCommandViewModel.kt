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

    /**
     * Resolves [VoiceCommand.OpenDisaster] and [VoiceCommand.OpenFirstAidCategory]
     * into their deep-link equivalents by doing a one-shot DB lookup.
     *
     * For [OpenDisaster]: looks up by the canonical Title-Case name that
     * [VoiceCommandManager.parseCommand] already placed in the command, then
     * emits [VoiceCommand.OpenDisasterDetail] with the real id + display name.
     * Falls back to [VoiceCommand.NavigateDisasters] if the entity isn't found
     * (shouldn't happen with seeded data, but handles gracefully).
     *
     * For [OpenFirstAidCategory]: same pattern → [VoiceCommand.OpenFirstAidDetail].
     *
     * All other commands pass through unchanged.
     */
    private suspend fun resolveCommand(command: VoiceCommand): VoiceCommand {
        return when (command) {

            is VoiceCommand.OpenDisaster -> {
                val entity = disasterRepository.getDisasterByName(command.disasterName)
                if (entity != null) {
                    VoiceCommand.OpenDisasterDetail(
                        disasterId   = entity.id,
                        disasterName = entity.name
                    )
                } else {
                    // Fallback: go to the disasters list — user can tap from there
                    VoiceCommand.NavigateDisasters
                }
            }

            is VoiceCommand.OpenFirstAidCategory -> {
                val entity = firstAidRepository.getCategoryByName(command.categoryName)
                if (entity != null) {
                    VoiceCommand.OpenFirstAidDetail(
                        categoryId   = entity.id,
                        categoryName = entity.name
                    )
                } else {
                    VoiceCommand.NavigateFirstAid
                }
            }

            // Every other command passes through as-is
            else -> command
        }
    }

    fun cancelListening() {
        voiceCommandManager.cancel()
    }

    fun stopListening() {
        voiceCommandManager.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        voiceCommandManager.destroy()
    }
}