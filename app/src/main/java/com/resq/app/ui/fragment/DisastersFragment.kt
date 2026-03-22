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
import com.resq.app.MainActivity
import com.resq.app.R
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

        // NEW — voice-commanded tab selection
        handleVoiceTabArgument()
        listenForVoiceTabSwitch()
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
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false
        binding.tilSearch.setOnClickListener { openGlobalSearch() }
        binding.etSearch.setOnClickListener  { openGlobalSearch() }
    }

    private fun openGlobalSearch() {
        findNavController().navigate(R.id.action_global_search)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.recommendedDisasters.collect { recommended ->
                        recommendedAdapter.submitList(recommended)
                        if (isRecommendedTab) {
                            binding.tvEmpty.isVisible     = recommended.isEmpty()
                            binding.rvDisasters.isVisible = recommended.isNotEmpty()
                        }
                    }
                }

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

    // ── Voice tab handling (NEW) ──────────────────────────────────────────────

    /**
     * Reads the one-shot "voiceTab" navigation argument and selects the
     * matching tab.  The argument is consumed so it doesn't re-fire on
     * configuration change or back-stack restore.
     */
    private fun handleVoiceTabArgument() {
        val voiceTab = arguments?.getString("voiceTab")
        if (!voiceTab.isNullOrEmpty()) {
            selectTabByLabel(voiceTab)
            arguments?.remove("voiceTab")
        }
    }

    /**
     * Listens for [MainActivity.REQUEST_SWITCH_TAB] fragment results so that
     * voice commands issued while this fragment is already visible can still
     * switch tabs.
     */
    private fun listenForVoiceTabSwitch() {
        parentFragmentManager.setFragmentResultListener(
            MainActivity.REQUEST_SWITCH_TAB,
            viewLifecycleOwner
        ) { _, bundle ->
            val tabLabel = bundle.getString(MainActivity.KEY_TAB_LABEL)
            if (!tabLabel.isNullOrEmpty()) {
                selectTabByLabel(tabLabel)
            }
        }
    }

    private fun selectTabByLabel(label: String) {
        val tabIndex = when (label) {
            "recommended" -> 0
            "all"         -> 1
            else          -> -1
        }
        if (tabIndex in 0 until binding.tabLayout.tabCount) {
            binding.tabLayout.getTabAt(tabIndex)?.select()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}