package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.resq.app.databinding.FragmentDisastersBinding
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        setupSearchView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        disasterAdapter = DisasterAdapter { disaster ->
            val action = DisastersFragmentDirections.actionDisastersToDetail(
                disasterId = disaster.id.toInt(),
                disasterName = disaster.name
            )
            findNavController().navigate(action)
        }

        binding.rvDisasters.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = disasterAdapter
        }
    }

    private fun setupSearchView() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.searchDisasters(text?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        disasterAdapter.submitList(state.filteredDisasters)

                        binding.tvEmpty.visibility =
                            if (state.filteredDisasters.isEmpty() && !state.isLoading) {
                                View.VISIBLE
                            } else {
                                View.GONE
                            }

                        binding.progressIndicator.visibility =
                            if (state.isLoading) View.VISIBLE else View.GONE
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