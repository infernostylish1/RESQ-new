package com.resq.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.resq.app.R
import com.resq.app.databinding.FragmentGlobalSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GlobalSearchFragment : Fragment() {

    private var _binding: FragmentGlobalSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlobalSearchViewModel by viewModels()
    private lateinit var searchAdapter: GlobalSearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlobalSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        viewModel.initializeQuery(arguments?.getString(ARG_INITIAL_QUERY))
        binding.etSearch.setText(viewModel.query.value)
        binding.etSearch.setSelection(binding.etSearch.length())
        setupSearch()
        setupDiscovery()
        observeViewModel()
        if (savedInstanceState == null && viewModel.query.value.isBlank()) {
            showKeyboard()
        }
        binding.btnBack.setOnClickListener {
            hideKeyboard()
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = GlobalSearchAdapter { result ->
            handleResultClick(result)
        }
        binding.rvResults.adapter = searchAdapter
    }

    private fun setupSearch() {
        binding.tilSearch.isEndIconCheckable = false
        binding.tilSearch.isEndIconVisible = binding.etSearch.length() > 0
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty()
            binding.tilSearch.isEndIconVisible = query.isNotEmpty()
            viewModel.onQueryChanged(query)
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.recordSearch()
                hideKeyboard()
                true
            } else {
                false
            }
        }
        binding.tilSearch.setEndIconOnClickListener {
            binding.etSearch.text?.clear()
            showKeyboard()
        }
    }

    private fun setupDiscovery() {
        ViewCompat.setAccessibilityHeading(binding.tvSuggestionsHeading, true)
        ViewCompat.setAccessibilityHeading(binding.tvHistoryHeading, true)
        showTerms(
            binding.chipSuggestions,
            resources.getStringArray(R.array.global_search_suggestions).toList()
        )
        binding.btnClearHistory.setOnClickListener {
            viewModel.clearHistory()
            binding.root.announceForAccessibility(getString(R.string.search_history_cleared))
        }
    }

    private fun showTerms(group: ChipGroup, terms: List<String>) {
        group.removeAllViews()
        terms.forEach { term ->
            val chip = layoutInflater.inflate(R.layout.item_search_term, group, false) as Chip
            chip.text = term
            chip.setOnClickListener {
                binding.etSearch.setText(term)
                binding.etSearch.setSelection(term.length)
                viewModel.recordSearch()
                hideKeyboard()
            }
            group.addView(chip)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.progressIndicator.isVisible = state.isLoading
                        binding.rvResults.isVisible = !state.isLoading && !state.isEmpty && !state.isIdle
                        binding.layoutIdle.isVisible = state.isIdle
                        binding.layoutEmpty.isVisible = !state.isLoading && state.isEmpty && !state.isIdle
                        searchAdapter.submitResults(state.results, state.query)
                    }
                }
                launch {
                    viewModel.history.collect { history ->
                        showTerms(binding.chipHistory, history)
                        binding.chipHistory.isVisible = history.isNotEmpty()
                        binding.btnClearHistory.isVisible = history.isNotEmpty()
                        binding.tvHistoryEmpty.isVisible = history.isEmpty()
                    }
                }
            }
        }
    }

    // ── Result click routing ──────────────────────────────────────────────────

    private fun handleResultClick(result: GlobalSearchResult) {
        viewModel.recordSearch()
        hideKeyboard()
        when (result) {
            is GlobalSearchResult.DisasterResult -> {
                val args = Bundle().apply {
                    putLong("disasterId", result.id)
                    putString("disasterName", result.title)
                }
                findNavController().navigate(R.id.nav_disaster_detail, args)
            }
            is GlobalSearchResult.FirstAidResult -> {
                val args = Bundle().apply {
                    putLong("categoryId", result.id)
                    putString("categoryName", result.title)
                }
                findNavController().navigate(R.id.nav_first_aid_detail, args)
            }
            is GlobalSearchResult.TipResult -> {
                val args = Bundle().apply {
                    putLong("disasterId", result.disasterId)
                    putString("disasterName", result.title)
                }
                findNavController().navigate(R.id.nav_tip_detail, args)
            }
            is GlobalSearchResult.ContactResult -> {
                // Navigate to contacts screen
                findNavController().navigate(R.id.nav_emergency_contacts)
            }
            is GlobalSearchResult.SectionHeader -> Unit // headers are not clickable
        }
    }

    // ── Keyboard helpers ──────────────────────────────────────────────────────

    private fun showKeyboard() {
        val searchView = binding.etSearch
        searchView.post {
            if (_binding?.etSearch !== searchView) return@post
            searchView.requestFocus()
            val imm = searchView.context.getSystemService<InputMethodManager>()
            imm?.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        binding.rvResults.adapter = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_INITIAL_QUERY = "arg_initial_query"
    }
}
