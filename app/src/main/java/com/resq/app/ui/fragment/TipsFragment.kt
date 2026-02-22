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
 * Tips fragment — shows all tips and favorites via TabLayout.
 *
 * Fixes applied:
 * - Replaced Spinner/ArrayAdapter with TabLayout.OnTabSelectedListener
 *   (layout has tab_layout with 2 TabItems, not a Spinner)
 * - binding.spinnerFilter → binding.tabLayout
 * - binding.btnRefresh removed (no such view in layout)
 * - binding.tvEmptyState → binding.emptyState (id: empty_state, a LinearLayout)
 * - binding.progressBar removed (no progress indicator in fragment_tips.xml)
 * - Layout uses et_search (TextInputEditText), not a SearchView
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
            onFavoriteClick = { tip ->
                viewModel.toggleFavorite(tip)
            },
            onShareClick = { tip ->
                shareTip(tip.tipText)
            },
            onReadAloudClick = { tip ->
                ttsManager.speak(tip.tipText)
            }
        )
        binding.rvTips.adapter = tipAdapter
    }

    private fun setupTabLayout() {
        // Layout has TabLayout with 2 tabs: "All Tips" (0) and "Favorites" (1)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.loadAllTips()
                    1 -> viewModel.loadFavoriteTips()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })
    }

    private fun setupSearch() {
        // Layout uses TextInputLayout + et_search
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
                        // Correct ID: empty_state (LinearLayout, not a TextView)
                        binding.emptyState.isVisible  = tips.isEmpty()
                        binding.rvTips.isVisible       = tips.isNotEmpty()
                    }
                }
                // No progress_indicator in fragment_tips.xml — no loading observer
            }
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}