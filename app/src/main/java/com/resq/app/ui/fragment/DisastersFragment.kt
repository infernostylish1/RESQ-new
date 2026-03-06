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
import com.google.android.material.tabs.TabLayout
import com.resq.app.data.entity.DisasterEntity
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

    // Recommended tab — single adapter, no "Other Disasters" section
    private lateinit var recommendedAdapter: DisasterAdapter

    // All Disasters tab — flat list of every disaster
    private lateinit var allAdapter: DisasterAdapter

    // true = Recommended tab, false = All Disasters tab
    private var isRecommendedTab = true

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
        setupTabLayout()
        setupSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        val onItemClick: (DisasterEntity) -> Unit = { disaster ->
            val action = DisastersFragmentDirections.actionDisastersToDetail(
                disasterId   = disaster.id,
                disasterName = disaster.name
            )
            findNavController().navigate(action)
        }

        recommendedAdapter = DisasterAdapter(onItemClick)
        allAdapter         = DisasterAdapter(onItemClick)

        // Start on Recommended tab with no headers or "Other" section
        showRecommendedTab()
    }

    private fun showRecommendedTab() {
        isRecommendedTab = true
        // Plain single adapter — ConcatAdapter, recommendedHeader,
        // otherHeader, and otherAdapter are removed entirely from this tab
        binding.rvDisasters.adapter = recommendedAdapter
    }

    private fun showAllTab() {
        isRecommendedTab = false
        binding.rvDisasters.adapter = allAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.etSearch.text?.clear()
                when (tab?.position) {
                    0 -> {
                        // Recommended list is already live from ViewModel init —
                        // no need to call loadAllDisasters() here
                        showRecommendedTab()
                    }
                    1 -> {
                        showAllTab()
                        viewModel.loadAllDisasters()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun setupSearch() {
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

                // Recommended tab: only profile-matched disasters, no "Other" section
                launch {
                    viewModel.recommendedDisasters.collect { recommended ->
                        recommendedAdapter.submitList(recommended)
                        if (isRecommendedTab) {
                            binding.tvEmpty.isVisible     = recommended.isEmpty()
                            binding.rvDisasters.isVisible = recommended.isNotEmpty()
                        }
                    }
                }

                // All Disasters tab: every disaster, flat list
                launch {
                    viewModel.disasters.collect { all ->
                        allAdapter.submitList(all)
                        if (!isRecommendedTab) {
                            binding.tvEmpty.isVisible     = all.isEmpty()
                            binding.rvDisasters.isVisible = all.isNotEmpty()
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
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