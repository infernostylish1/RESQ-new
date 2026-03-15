package com.resq.app.ui.voice

import android.animation.ObjectAnimator
import android.Manifest
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

    // Pulse animator for the mic icon while listening
    private var pulseAnimator: ObjectAnimator? = null

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
        setupPulseAnimator()
        observeRecognitionState()
        setupButtons()
    }

    private fun setupPulseAnimator() {
        pulseAnimator = ObjectAnimator.ofFloat(binding.ivMicPulse, "scaleX", 1f, 1.3f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        // Also animate scaleY in sync
        val pulseY = ObjectAnimator.ofFloat(binding.ivMicPulse, "scaleY", 1f, 1.3f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        // Start both together when needed — stored separately
        binding.ivMicPulse.tag = pulseY
    }

    private fun startMicAnimation() {
        binding.ivMicPulse.visibility = View.VISIBLE
        pulseAnimator?.start()
        @Suppress("UNCHECKED_CAST")
        (binding.ivMicPulse.tag as? ObjectAnimator)?.start()
    }

    private fun stopMicAnimation() {
        pulseAnimator?.cancel()
        @Suppress("UNCHECKED_CAST")
        (binding.ivMicPulse.tag as? ObjectAnimator)?.cancel()
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
            dismiss()
        }
    }

    private fun checkPermissionAndListen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startListening()
            }
            else -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
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
                binding.tvVoiceStatus.setText(R.string.voice_tap_to_speak)
                stopMicAnimation()
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
            }
            is RecognitionState.PartialResult -> {
                binding.tvVoiceStatus.text = state.text
            }
            is RecognitionState.Result -> {
                binding.tvVoiceStatus.text = getString(
                    R.string.voice_heard_format, state.text
                )
                stopMicAnimation()
                binding.root.postDelayed({ dismissAllowingStateLoss() }, 800)
            }
            is RecognitionState.Error -> {
                binding.tvVoiceStatus.text = state.message
                stopMicAnimation()
                binding.btnStartListening.isEnabled = true
                binding.btnStartListening.setText(R.string.voice_retry)
            }
            is RecognitionState.Ready -> {
                binding.btnStartListening.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VoiceCommandBottomSheet"
    }
}