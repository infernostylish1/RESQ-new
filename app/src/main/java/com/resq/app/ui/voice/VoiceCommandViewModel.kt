package com.resq.app.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resq.app.util.RecognitionState
import com.resq.app.util.VoiceCommand
import com.resq.app.util.VoiceCommandManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceCommandViewModel @Inject constructor(
    private val voiceCommandManager: VoiceCommandManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val recognitionState: StateFlow<RecognitionState> =
        voiceCommandManager.recognitionState

    // One-shot events consumed by MainActivity
    private val _commandEvent = MutableSharedFlow<VoiceCommand>()
    val commandEvent: SharedFlow<VoiceCommand> = _commandEvent.asSharedFlow()

    init {
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