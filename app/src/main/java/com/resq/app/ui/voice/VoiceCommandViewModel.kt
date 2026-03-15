package com.resq.app.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val voiceCommandManager: VoiceCommandManager
) : ViewModel() {

    val recognitionState: StateFlow<RecognitionState> =
        voiceCommandManager.recognitionState

    // One-shot events consumed by MainActivity (or whoever hosts the bottom sheet)
    private val _commandEvent = MutableSharedFlow<VoiceCommand>()
    val commandEvent: SharedFlow<VoiceCommand> = _commandEvent.asSharedFlow()

    /**
     * Must be called from a Fragment/Activity with a valid window context.
     * SpeechRecognizer requires a context attached to a window on the main thread.
     */
    fun initialize(context: Context) {
        voiceCommandManager.initialize(context)
    }

    fun startListening() {
        voiceCommandManager.startListening { command ->
            viewModelScope.launch {
                _commandEvent.emit(command)
            }
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