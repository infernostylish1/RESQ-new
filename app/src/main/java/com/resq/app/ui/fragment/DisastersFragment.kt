package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.databinding.FragmentDisastersBinding
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Disasters list fragment.
 *
 * Fixes applied:
 * - Replaced SearchView with TextInputLayout/TextInputEditText (et_search)
 * - binding.tvEmptyState → binding.tvEmpty (matches fragment_disasters.xml id: tv_empty)
 * - binding.progressBar → binding.progressIndicator (matches id: progress_indicator)
 */
@AndroidEntryPoint
class DisastersFragment : Fragment() {

    private var _binding: FragmentDisastersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterViewModel by viewModels()
    private lateinit var disasterAdapter: DisasterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisastersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        disasterAdapter = DisasterAdapter { disaster ->
            val action = DisastersFragmentDirections
                .actionDisastersToDetail(
                    disasterId   = disaster.id,
                    disasterName = disaster.name
                )
            findNavController().navigate(action)
        }
        binding.rvDisasters.adapter = disasterAdapter
    }

    private fun setupSearch() {
        // Layout uses TextInputLayout + TextInputEditText, not SearchView
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            if (query.isEmpty()) {
                viewModel.loadAllDisasters()
            } else {
                viewModel.searchDisasters(query)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.disasters.collect { disasters ->
                        disasterAdapter.submitList(disasters)
                        // Correct ID: tv_empty (not tvEmptyState)
                        binding.tvEmpty.isVisible = disasters.isEmpty()
                        binding.rvDisasters.isVisible = disasters.isNotEmpty()
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        // Correct ID: progress_indicator (not progressBar)
                        binding.progressIndicator.isVisible = loading
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}