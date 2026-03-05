package com.resq.app.ui.fragment

import android.content.Intent
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
import com.google.android.material.tabs.TabLayout
import com.resq.app.R
import com.resq.app.databinding.FragmentTipsBinding
import com.resq.app.ui.adapter.TipAdapter
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import com.resq.app.util.TextToSpeechManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tips fragment.
 *
 * Changes from original:
 * - Tab 0: "Recommended" — calls viewModel.loadRecommendedTipsForTab()
 * - Tab 1: "All Tips"    — calls viewModel.loadAllTips()
 * - Favorites are managed via the star icon on each tip card (unchanged behavior).
 * - No structural layout changes — fragment_tips.xml tab text strings updated separately.
 */
@AndroidEntryPoint
class TipsFragment : Fragment() {

    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterTipsViewModel by viewModels()

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var tipAdapter: TipAdapter

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

    private fun setupRecyclerView() {
        tipAdapter = TipAdapter(
            onFavoriteClick  = { tip -> viewModel.toggleFavorite(tip) },
            onShareClick     = { tip -> shareTip(tip.tipText) },
            onReadAloudClick = { tip -> ttsManager.speak(tip.tipText) }
        )
        binding.rvTips.adapter = tipAdapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Clear search text when switching tabs so the new tab
                // shows its full unfiltered list immediately.
                binding.etSearch.text?.clear()

                when (tab?.position) {
                    0 -> viewModel.loadRecommendedTipsForTab() // CHANGED: was loadAllTips()
                    1 -> viewModel.loadAllTips()               // CHANGED: was loadFavoriteTips()
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
                viewModel.clearSearch()
            } else {
                viewModel.searchTips(query)
            }
        }
    }

    private fun shareTip(tipText: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tipText)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tipsWithCategory.collect { tips ->
                        tipAdapter.submitList(tips)
                        binding.emptyState.isVisible = tips.isEmpty()
                        binding.rvTips.isVisible     = tips.isNotEmpty()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}