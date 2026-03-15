package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.resq.app.R
import com.resq.app.databinding.FragmentDisasterDetailBinding
import com.resq.app.ui.adapter.PreparationStepAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import com.resq.app.util.TextToSpeechManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Disaster detail fragment — shows info + before/during/after preparation steps.
 *
 * Filters preparations by the `phase` String column in DisasterPreparationEntity
 * (values: "Before", "During", "After"), NOT by SituationType enum.
 */
@AndroidEntryPoint
class DisasterDetailFragment : Fragment() {

    private var _binding: FragmentDisasterDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterViewModel by viewModels()
    private val args: DisasterDetailFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var preparationAdapter: PreparationStepAdapter

    /** Tracks the currently selected temporal phase as a String matching the DB column. */
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
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
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

    // ── Header image ──────────────────────────────────────────────────────────

    private fun loadHeaderImage(imageResName: String) {
        val context = requireContext()
        val resId   = context.resources.getIdentifier(
            imageResName, "drawable", context.packageName
        )
        if (resId != 0) {
            binding.ivHeader.setImageResource(resId)
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PHASE_BEFORE = "Before"
        const val PHASE_DURING = "During"
        const val PHASE_AFTER  = "After"
    }
}