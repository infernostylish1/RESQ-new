// com/resq/app/util/RecognitionState.kt
package com.resq.app.util

sealed class RecognitionState {
    data object Idle           : RecognitionState()
    data object Ready          : RecognitionState()
    data object Starting       : RecognitionState()
    data object Listening      : RecognitionState()
    data object Processing     : RecognitionState()
    data class  PartialResult(val text: String)                        : RecognitionState()
    data class  Result       (val text: String, val command: VoiceCommand) : RecognitionState()
    data class  Error        (val message: String)                     : RecognitionState()
}