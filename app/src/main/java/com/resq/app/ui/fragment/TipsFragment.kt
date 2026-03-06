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
import com.resq.app.databinding.FragmentTipsBinding
import com.resq.app.ui.adapter.TipDisasterGroupAdapter
import com.resq.app.ui.viewmodel.DisasterTipGroup
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TipsFragment : Fragment() {

    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterTipsViewModel by viewModels()

    private lateinit var groupAdapter: TipDisasterGroupAdapter

    // Two separate caches — one per tab — so each observer stores
    // its data unconditionally without racing on selectedTabPosition.
    private var recommendedGroups: List<DisasterTipGroup> = emptyList()
    private var allGroups: List<DisasterTipGroup> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabLayout()
        setupSearch()
        observeViewModel()
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        groupAdapter = TipDisasterGroupAdapter { group ->
            navigateToTipDetail(group)
        }
        binding.rvTips.adapter = groupAdapter
    }

    private fun navigateToTipDetail(group: DisasterTipGroup) {
        val action = TipsFragmentDirections.actionTipsToTipDetail(
            disasterId   = group.disasterId,
            disasterName = group.disasterName
        )
        findNavController().navigate(action)
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                binding.etSearch.text?.clear()
                when (tab?.position) {
                    0 -> {
                        viewModel.loadRecommendedTipsForTab()
                        // Immediately show the cached list — no wait for emission
                        showGroups(recommendedGroups)
                    }
                    1 -> {
                        viewModel.loadAllTips()
                        // Immediately show the cached list — no wait for emission
                        showGroups(allGroups)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            filterGroups(query)
        }
    }

    private fun filterGroups(query: String) {
        // Pick the correct source list based on which tab is active
        val source = if (binding.tabLayout.selectedTabPosition == 0) {
            recommendedGroups
        } else {
            allGroups
        }
        val filtered = if (query.isBlank()) source else {
            source.filter { group ->
                group.disasterName.contains(query, ignoreCase = true) ||
                        group.disasterNameTagalog.contains(query, ignoreCase = true)
            }
        }
        showGroups(filtered)
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressIndicator.isVisible = state.isLoading
                    }
                }

                // Always store recommended groups unconditionally.
                // Only push to the adapter when tab 0 is active so the
                // All tab is not overwritten by a late recommended emission.
                launch {
                    viewModel.recommendedDisasterGroups.collect { groups ->
                        recommendedGroups = groups
                        if (binding.tabLayout.selectedTabPosition == 0) {
                            val query = binding.etSearch.text?.toString().orEmpty().trim()
                            filterGroups(query)
                        }
                    }
                }

                // Always store all groups unconditionally.
                // Only push to the adapter when tab 1 is active.
                launch {
                    viewModel.allDisasterGroups.collect { groups ->
                        allGroups = groups
                        if (binding.tabLayout.selectedTabPosition == 1) {
                            val query = binding.etSearch.text?.toString().orEmpty().trim()
                            filterGroups(query)
                        }
                    }
                }
            }
        }
    }

    private fun showGroups(groups: List<DisasterTipGroup>) {
        groupAdapter.submitList(groups)
        binding.emptyState.isVisible = groups.isEmpty()
        binding.rvTips.isVisible     = groups.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}