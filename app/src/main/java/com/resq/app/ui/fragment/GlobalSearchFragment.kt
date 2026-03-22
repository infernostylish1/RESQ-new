// com/resq/app/ui/search/GlobalSearchFragment.kt
package com.resq.app.ui.search

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.R
import com.resq.app.databinding.FragmentGlobalSearchBinding
import com.resq.app.util.setupWithNavController
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
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        showKeyboard()

        // If launched with a pre-filled query (e.g. from voice command)
        arguments?.getString(ARG_INITIAL_QUERY)?.let { query ->
            if (query.isNotBlank()) {
                binding.etSearch.setText(query)
                binding.etSearch.setSelection(query.length)
                viewModel.onQueryChanged(query)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupRecyclerView() {
        searchAdapter = GlobalSearchAdapter { result ->
            handleResultClick(result)
        }
        binding.rvResults.adapter = searchAdapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty()
            viewModel.onQueryChanged(query)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressIndicator.isVisible = state.isLoading
                    binding.rvResults.isVisible         = !state.isLoading && !state.isEmpty && !state.isIdle
                    binding.layoutIdle.isVisible        = state.isIdle
                    binding.layoutEmpty.isVisible       = !state.isLoading && state.isEmpty && !state.isIdle

                    if (!state.isLoading) {
                        searchAdapter.submitList(state.results)
                    }
                }
            }
        }
    }

    // ── Result click routing ──────────────────────────────────────────────────

    private fun handleResultClick(result: GlobalSearchResult) {
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
        binding.etSearch.post {
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService<InputMethodManager>()
            imm?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService<InputMethodManager>()
        imm?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_INITIAL_QUERY = "arg_initial_query"
    }
}