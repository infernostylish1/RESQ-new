package com.resq.app.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.resq.app.MainActivity
import com.resq.app.R
import com.resq.app.util.setupSettingsAction
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.databinding.FragmentDisastersBinding
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.adapter.TipDisasterGroupAdapter
import com.resq.app.ui.viewmodel.DisasterTipGroup
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import com.resq.app.ui.viewmodel.DisasterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DisastersFragment : Fragment() {

    private var _binding: FragmentDisastersBinding? = null
    private val binding get() = _binding!!
    private val filterLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        updateFilterPlacement()
    }

    private val disasterViewModel: DisasterViewModel by viewModels()
    private val tipsViewModel: DisasterTipsViewModel by viewModels()

    private lateinit var disasterAdapter: DisasterAdapter
    private lateinit var tipsAdapter: TipDisasterGroupAdapter

    private var recommendedDisasters: List<DisasterEntity> = emptyList()
    private var allDisasters: List<DisasterEntity> = emptyList()
    private var recommendedTips: List<DisasterTipGroup> = emptyList()
    private var allTips: List<DisasterTipGroup> = emptyList()

    private enum class MainTab { INFORMATION, TIPS }
    private enum class FilterMode { RECOMMENDED, ALL }

    private var currentMainTab = MainTab.INFORMATION

    // Single shared filter — persists across Information <-> Tips tab switches.
    private var currentFilter = FilterMode.RECOMMENDED

    private val filterPreferences by lazy {
        requireContext().getSharedPreferences("disaster_screen", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentMainTab = if (savedInstanceState?.getString("main_tab") == MainTab.TIPS.name) {
            MainTab.TIPS
        } else {
            MainTab.INFORMATION
        }
        currentFilter = if (filterPreferences.getBoolean("show_all_disasters", false)) {
            FilterMode.ALL
        } else {
            FilterMode.RECOMMENDED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDisastersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupToolbar()
        setupFilter()
        setupMainTabs()
        setupSearch()
        observeViewModels()
        handleVoiceArguments()
        listenForVoiceSwitch()
        renderCurrentTab()
    }

    // ── Adapters ──────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        disasterAdapter = DisasterAdapter { disaster ->
            val action = DisastersFragmentDirections.actionDisastersToDetail(
                disasterId   = disaster.id,
                disasterName = disaster.name
            )
            findNavController().navigate(action)
        }
        tipsAdapter = TipDisasterGroupAdapter { group ->
            val action = DisastersFragmentDirections.actionDisastersToTipDetail(
                disasterId   = group.disasterId,
                disasterName = group.disasterName
            )
            findNavController().navigate(action)
        }
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setupSettingsAction(this)

        // Retain menu dispatch for accessibility services that invoke the item directly.
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(R.id.nav_settings)
                    true
                }
                else -> false
            }

        }
    }

    private fun setupFilter() {
        binding.floatingActionsCenter.setGuidelineEnd(fabCenterFromBottom())
        binding.btnFilter.updateLayoutParams<MarginLayoutParams> {
            marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin) +
                resources.getDimensionPixelSize(R.dimen.fab_size) +
                resources.getDimensionPixelSize(R.dimen.spacing_md)
        }
        // Recheck actual card geometry after data, tab, text size or viewport changes.
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(filterLayoutListener)
        updateListBottomPadding(fabCenterFromBottom())
        binding.btnFilter.setOnClickListener {
            setFilterMode(if (currentFilter == FilterMode.RECOMMENDED) FilterMode.ALL else FilterMode.RECOMMENDED)
        }
        updateFilterLabel()
    }

    private fun fabCenterFromBottom() =
        resources.getDimensionPixelSize(R.dimen.fab_main_margin_bottom) -
            resources.getDimensionPixelSize(R.dimen.bottom_nav_height) +
            resources.getDimensionPixelSize(R.dimen.fab_size) / 2

    private fun updateFilterPlacement() {
        val views = _binding ?: return
        val list = views.rvContent
        updateListBottomPadding(fabCenterFromBottom())
        if (views.btnFilter.isLayoutRequested ||
            (list.isVisible && (list.isLayoutRequested || list.height == 0))) return

        // Check both directions: reaching the end of a long list must not unstick the filter.
        val lastCard = list.adapter?.itemCount?.takeIf { it > 0 }?.let {
            list.findViewHolderForAdapterPosition(it - 1)?.itemView
        }
        val inlineTop = lastCard?.let {
            list.top + it.bottom + resources.getDimensionPixelSize(R.dimen.spacing_md)
        }
        val fits = list.isVisible && inlineTop != null &&
            inlineTop + views.btnFilter.height <= list.bottom &&
            !list.canScrollVertically(-1) && !list.canScrollVertically(1)
        val guideline = views.floatingActionsCenter
        val params = guideline.layoutParams as ConstraintLayout.LayoutParams
        if (fits) {
            val center = inlineTop!! + (views.btnFilter.height + 1) / 2
            if (params.guideBegin != center || params.guideEnd != -1) {
                guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideBegin = center
                    guideEnd = -1
                    guidePercent = -1f
                }
            }
        } else {
            val centerFromBottom = fabCenterFromBottom()
            if (params.guideEnd != centerFromBottom || params.guideBegin != -1) {
                guideline.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    guideBegin = -1
                    guideEnd = centerFromBottom
                    guidePercent = -1f
                }
            }
        }
    }

    private fun updateListBottomPadding(fabCenterFromBottom: Int) {
        // Reserve the same clearance in both modes so placement cannot change the scrollability.
        // The last card stays clear of the FAB and even a taller filter label when scrolled to the end.
        val controlsHeight = maxOf(binding.btnFilter.height, resources.getDimensionPixelSize(R.dimen.fab_size))
        val padding = fabCenterFromBottom + (controlsHeight + 1) / 2 +
            resources.getDimensionPixelSize(R.dimen.spacing_md)
        if (binding.rvContent.paddingBottom != padding) {
            binding.rvContent.updatePadding(bottom = padding)
        }
    }

    private fun setFilterMode(mode: FilterMode) {
        currentFilter = mode
        filterPreferences.edit().putBoolean("show_all_disasters", mode == FilterMode.ALL).apply()
        updateFilterLabel()
        renderCurrentTab()
    }

    private fun updateFilterLabel() {
        val res = when (currentFilter) {
            FilterMode.RECOMMENDED -> R.string.disasters_tab_recommended
            FilterMode.ALL         -> R.string.disasters_tab_all
        }
        binding.btnFilter.text = getString(res)
        val next = if (currentFilter == FilterMode.RECOMMENDED) {
            R.string.disasters_tab_all
        } else {
            R.string.disasters_tab_recommended
        }
        binding.btnFilter.contentDescription = getString(
            R.string.disasters_filter_description, getString(res), getString(next)
        )
    }

    // ── Main tabs (Information / Tips) ────────────────────────────────────────

    private fun setupMainTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMainTab = if (tab?.position == 0) MainTab.INFORMATION else MainTab.TIPS
                renderCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
        binding.tabLayout.getTabAt(if (currentMainTab == MainTab.INFORMATION) 0 else 1)?.select()
    }

    private fun renderCurrentTab() {
        when (currentMainTab) {
            MainTab.INFORMATION -> {
                binding.rvContent.adapter = disasterAdapter
                val list = when (currentFilter) {
                    FilterMode.RECOMMENDED -> recommendedDisasters
                    FilterMode.ALL         -> allDisasters
                }
                disasterAdapter.submitList(list)
                showEmpty(list.isEmpty())
            }
            MainTab.TIPS -> {
                binding.rvContent.adapter = tipsAdapter
                val list = when (currentFilter) {
                    FilterMode.RECOMMENDED -> recommendedTips
                    FilterMode.ALL         -> allTips
                }
                tipsAdapter.submitList(list)
                showEmpty(list.isEmpty())
            }
        }
    }

    private fun showEmpty(empty: Boolean) {
        binding.tvEmpty.isVisible = empty
        binding.rvContent.isVisible = !empty
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false
        binding.tilSearch.isEndIconVisible = false
        binding.tilSearch.setStartIconContentDescription(R.string.search_field_hint)
        binding.tilSearch.setStartIconOnClickListener { openGlobalSearch() }
        binding.tilSearch.setOnClickListener { openGlobalSearch() }
        binding.etSearch.setOnClickListener { openGlobalSearch() }
    }

    private fun openGlobalSearch() {
        findNavController().navigate(R.id.action_global_search)
    }

    // ── View models ───────────────────────────────────────────────────────────

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    disasterViewModel.recommendedDisasters.collect {
                        recommendedDisasters = it
                        if (currentMainTab == MainTab.INFORMATION &&
                            currentFilter == FilterMode.RECOMMENDED) renderCurrentTab()
                    }
                }
                launch {
                    disasterViewModel.disasters.collect {
                        allDisasters = it
                        if (currentMainTab == MainTab.INFORMATION &&
                            currentFilter == FilterMode.ALL) renderCurrentTab()
                    }
                }
                launch {
                    disasterViewModel.isLoading.collect {
                        binding.progressIndicator.isVisible = it
                    }
                }
                launch {
                    tipsViewModel.recommendedDisasterGroups.collect {
                        recommendedTips = it
                        if (currentMainTab == MainTab.TIPS &&
                            currentFilter == FilterMode.RECOMMENDED) renderCurrentTab()
                    }
                }
                launch {
                    tipsViewModel.allDisasterGroups.collect {
                        allTips = it
                        if (currentMainTab == MainTab.TIPS &&
                            currentFilter == FilterMode.ALL) renderCurrentTab()
                    }
                }
            }
        }
    }

    // ── Voice handling ────────────────────────────────────────────────────────

    private fun handleVoiceArguments() {
        val mainTab   = arguments?.getString(MainActivity.ARG_VOICE_MAIN_TAB).orEmpty()
        val filterTab = arguments?.getString(MainActivity.ARG_VOICE_FILTER_TAB).orEmpty()

        if (mainTab.isNotEmpty()) selectMainTabByLabel(mainTab)
        if (filterTab.isNotEmpty()) selectFilterByLabel(filterTab)

        arguments?.remove(MainActivity.ARG_VOICE_MAIN_TAB)
        arguments?.remove(MainActivity.ARG_VOICE_FILTER_TAB)
    }

    private fun listenForVoiceSwitch() {
        parentFragmentManager.setFragmentResultListener(
            MainActivity.REQUEST_SWITCH_TAB,
            viewLifecycleOwner
        ) { _, bundle ->
            val mainTab   = bundle.getString(MainActivity.ARG_VOICE_MAIN_TAB)
            val filterTab = bundle.getString(MainActivity.ARG_VOICE_FILTER_TAB)
            if (!mainTab.isNullOrEmpty()) selectMainTabByLabel(mainTab)
            if (!filterTab.isNullOrEmpty()) selectFilterByLabel(filterTab)
        }
    }

    private fun selectMainTabByLabel(label: String) {
        val index = when (label.lowercase()) {
            "information", "info", "disasters" -> 0
            "tips", "tip"                       -> 1
            else                                -> -1
        }
        if (index in 0 until binding.tabLayout.tabCount) {
            binding.tabLayout.getTabAt(index)?.select()
        }
    }

    private fun selectFilterByLabel(label: String) {
        val mode = when (label.lowercase()) {
            "all"         -> FilterMode.ALL
            "recommended" -> FilterMode.RECOMMENDED
            else          -> return
        }
        setFilterMode(mode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("main_tab", currentMainTab.name)
    }

    override fun onDestroyView() {
        binding.root.viewTreeObserver.removeOnGlobalLayoutListener(filterLayoutListener)
        super.onDestroyView()
        _binding = null
    }
}
