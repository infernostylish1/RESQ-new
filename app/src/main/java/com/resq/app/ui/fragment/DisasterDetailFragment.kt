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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.resq.app.data.model.SituationType
import com.resq.app.databinding.FragmentDisasterDetailBinding
import com.resq.app.ui.adapter.PreparationStepAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import com.resq.app.util.TextToSpeechManager
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

    private var currentSituationType: SituationType = SituationType.GENERAL

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
        setupClickListeners()
        observeViewModel()

        viewModel.selectDisaster(args.disasterId.toLong())
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupChipGroup() {
        binding.chipGroupSituation.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                currentSituationType = when (checkedIds.first()) {
                    binding.chipBefore.id -> SituationType.GENERAL
                    binding.chipDuring.id -> SituationType.URBAN
                    binding.chipAfter.id -> SituationType.COASTAL
                    else -> SituationType.GENERAL
                }
                updatePreparationsList()
            }
        }
        // Select first chip by default
        binding.chipBefore.isChecked = true
    }

    private fun setupRecyclerView() {
        preparationAdapter = PreparationStepAdapter()

        binding.rvPreparations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = preparationAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabReadAloud.setOnClickListener {
            readCurrentContentAloud()
        }
    }

    private fun readCurrentContentAloud() {
        val textToRead = viewModel.getContentForTTS()
        if (textToRead.isNotEmpty()) {
            ttsManager.speak(textToRead)
        }
    }

    private fun updatePreparationsList() {
        val preparations = viewModel.getPreparationsBySituation(currentSituationType)
        preparationAdapter.submitList(preparations)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedDisaster.collect { disaster ->
                        disaster?.let {
                            binding.collapsingToolbar.title = it.name
                            binding.tvDescription.text = it.description
                        }
                    }
                }

                launch {
                    viewModel.preparations.collect { preparations ->
                        preparationAdapter.submitList(preparations)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        // Handle loading state if needed
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ttsManager.stop()
        _binding = null
    }
}