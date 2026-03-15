package com.resq.app.ui.voice

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.resq.app.R
import com.resq.app.databinding.BottomSheetVoiceCommandBinding
import com.resq.app.util.RecognitionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceCommandBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVoiceCommandBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VoiceCommandViewModel by activityViewModels()

    // Pulse animators stored as fields — avoids the fragile .tag approach
    private var pulseAnimatorX: ObjectAnimator? = null
    private var pulseAnimatorY: ObjectAnimator? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            binding.tvVoiceStatus.setText(R.string.voice_permission_denied)
            binding.btnStartListening.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVoiceCommandBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize here so SpeechRecognizer gets a proper window-attached context
        viewModel.initialize(requireContext())

        setupPulseAnimator()
        observeRecognitionState()
        setupButtons()
    }

    private fun setupPulseAnimator() {
        pulseAnimatorX = ObjectAnimator.ofFloat(
            binding.ivMicPulse, "scaleX", 1f, 1.3f
        ).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        pulseAnimatorY = ObjectAnimator.ofFloat(
            binding.ivMicPulse, "scaleY", 1f, 1.3f
        ).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun startMicAnimation() {
        binding.ivMicPulse.visibility = View.VISIBLE
        pulseAnimatorX?.start()
        pulseAnimatorY?.start()
    }

    private fun stopMicAnimation() {
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()
        // Reset scale before hiding to avoid a frozen enlarged icon on next show
        binding.ivMicPulse.scaleX = 1f
        binding.ivMicPulse.scaleY = 1f
        binding.ivMicPulse.visibility = View.INVISIBLE
    }

    private fun setupButtons() {
        binding.btnStartListening.setOnClickListener {
            checkPermissionAndListen()
        }
        binding.btnCancel.setOnClickListener {
            viewModel.cancelListening()
            dismissAllowingStateLoss()
        }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun observeRecognitionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recognitionState.collect { state ->
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: RecognitionState) {
        when (state) {
            is RecognitionState.Idle -> {
                stopMicAnimation()
                binding.tvVoiceStatus.setText(R.string.voice_tap_to_speak)
                binding.btnStartListening.isEnabled = true
                binding.btnStartListening.setText(R.string.voice_start)
            }

            is RecognitionState.Ready -> {
                // Recognizer is initialized and ready — reset button in case
                // this fires after an error or on re-open
                binding.btnStartListening.isEnabled = true
                binding.btnStartListening.setText(R.string.voice_start)
            }

            is RecognitionState.Starting -> {
                binding.tvVoiceStatus.setText(R.string.voice_starting)
                binding.btnStartListening.isEnabled = false
            }

            is RecognitionState.Listening -> {
                binding.tvVoiceStatus.setText(R.string.voice_listening)
                startMicAnimation()
                binding.btnStartListening.isEnabled = false
            }

            is RecognitionState.Processing -> {
                binding.tvVoiceStatus.setText(R.string.voice_processing)
                stopMicAnimation()
                binding.btnStartListening.isEnabled = false
            }

            is RecognitionState.PartialResult -> {
                // Show live transcription as user speaks
                binding.tvVoiceStatus.text = state.text
            }

            is RecognitionState.Result -> {
                stopMicAnimation()
                binding.tvVoiceStatus.text = getString(
                    R.string.voice_heard_format, state.text
                )
                // Brief delay so the user sees what was heard before dismissal
                binding.root.postDelayed({ dismissAllowingStateLoss() }, 800)
            }

            is RecognitionState.Error -> {
                stopMicAnimation()
                binding.tvVoiceStatus.text = state.message
                binding.btnStartListening.isEnabled = true
                binding.btnStartListening.setText(R.string.voice_retry)
            }
        }
    }

    override fun onDestroyView() {
        // Cancel and null out animators before nulling the binding
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()
        pulseAnimatorX = null
        pulseAnimatorY = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VoiceCommandBottomSheet"
    }
}