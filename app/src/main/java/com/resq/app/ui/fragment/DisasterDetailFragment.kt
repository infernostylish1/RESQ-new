package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.resq.app.MainActivity
import com.resq.app.R
import com.resq.app.databinding.FragmentDisasterDetailBinding
import com.resq.app.ui.adapter.PreparationStepAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import com.resq.app.util.TextToSpeechManager
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DisasterDetailFragment : Fragment() {

    private var _binding: FragmentDisasterDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterViewModel by viewModels()
    private val args: DisasterDetailFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var preparationAdapter: PreparationStepAdapter

    private var currentPhase: String = PHASE_BEFORE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisasterDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupChipGroup()
        setupRecyclerView()
        viewModel.loadDisaster(args.disasterId)
        observeViewModel()
        setupVoiceListeners()

        val voicePhase = arguments?.getString(MainActivity.ARG_VOICE_PHASE)
        if (!voicePhase.isNullOrEmpty()) {
            selectPhaseByLabel(voicePhase)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupChipGroup() {
        binding.chipBefore.isChecked = true

        binding.chipGroupSituation.setOnCheckedStateChangeListener { _, checkedIds ->
            currentPhase = when {
                checkedIds.contains(R.id.chip_before) -> PHASE_BEFORE
                checkedIds.contains(R.id.chip_during) -> PHASE_DURING
                checkedIds.contains(R.id.chip_after)  -> PHASE_AFTER
                else                                  -> PHASE_BEFORE
            }
            viewModel.loadPreparationsByPhase(args.disasterId, currentPhase)
        }
    }

    private fun setupRecyclerView() {
        preparationAdapter = PreparationStepAdapter()
        binding.rvPreparations.adapter = preparationAdapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.selectedDisaster.collect { disaster ->
                        disaster ?: return@collect
                        binding.collapsingToolbar.title = disaster.name
                        binding.tvDescription.text      = disaster.description
                        loadHeaderImage(disaster.imageResName)
                        viewModel.loadPreparationsByPhase(args.disasterId, currentPhase)
                    }
                }

                launch {
                    viewModel.preparations.collect { steps ->
                        preparationAdapter.submitList(steps)
                    }
                }
            }
        }
    }

    // ─── Lifecycle: pause/resume video players ──────────────────────────────────

    override fun onPause() {
        super.onPause()
        preparationAdapter.pauseAllPlayers()
    }

    override fun onResume() {
        super.onResume()
        preparationAdapter.resumeAllPlayers()
    }

    // ─── Voice command listeners ───────────────────────────────────────────────

    private fun setupVoiceListeners() {
        setFragmentResultListener(MainActivity.REQUEST_SCROLL) { _, bundle ->
            when (bundle.getString(MainActivity.KEY_SCROLL)) {
                MainActivity.SCROLL_UP   -> binding.nestedScrollView.smoothScrollTo(0, 0)
                MainActivity.SCROLL_DOWN -> binding.nestedScrollView.smoothScrollTo(
                    0, binding.nestedScrollView.getChildAt(0).height
                )
            }
        }

        setFragmentResultListener(MainActivity.REQUEST_SWITCH_TAB) { _, bundle ->
            val label = bundle.getString(MainActivity.KEY_TAB_LABEL) ?: return@setFragmentResultListener
            selectPhaseByLabel(label)
        }
    }

    private fun selectPhaseByLabel(label: String) {
        when (label.lowercase()) {
            "before" -> {
                binding.chipBefore.isChecked = true
                currentPhase = PHASE_BEFORE
                viewModel.loadPreparationsByPhase(args.disasterId, PHASE_BEFORE)
            }
            "during" -> {
                binding.chipDuring.isChecked = true
                currentPhase = PHASE_DURING
                viewModel.loadPreparationsByPhase(args.disasterId, PHASE_DURING)
            }
            "after" -> {
                binding.chipAfter.isChecked = true
                currentPhase = PHASE_AFTER
                viewModel.loadPreparationsByPhase(args.disasterId, PHASE_AFTER)
            }
        }
    }

    /**
     * Loads the header image (static or GIF) using Glide.
     * Supports any image format Glide can handle, including animated GIFs.
     */
    private fun loadHeaderImage(imageResName: String) {
        val context = requireContext()
        val resId = context.resources.getIdentifier(
            imageResName, "drawable", context.packageName
        )
        if (resId != 0) {
            Glide.with(context)
                .load(resId)
                .centerCrop()
                .into(binding.ivHeader)
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        preparationAdapter.releaseAllPlayers()  // ← ADDED: prevents memory leak
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PHASE_BEFORE = "Before"
        const val PHASE_DURING = "During"
        const val PHASE_AFTER  = "After"
    }
}