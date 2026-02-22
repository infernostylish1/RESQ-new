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
import com.resq.app.databinding.FragmentFirstAidBinding
import com.resq.app.ui.adapter.FirstAidCategoryAdapter
import com.resq.app.ui.viewmodel.FirstAidViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * First Aid categories grid fragment.
 *
 * Fixes applied:
 * - Replaced SearchView with TextInputLayout/et_search
 * - binding.tvEmptyState → binding.tvEmpty (id: tv_empty)
 * - Removed programmatic LinearLayoutManager — layout XML declares GridLayoutManager (spanCount=2)
 * - No progressBar in this layout — removed
 */
@AndroidEntryPoint
class FirstAidFragment : Fragment() {

    private var _binding: FragmentFirstAidBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirstAidViewModel by viewModels()
    private lateinit var categoryAdapter: FirstAidCategoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstAidBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        categoryAdapter = FirstAidCategoryAdapter { category ->
            val action = FirstAidFragmentDirections
                .actionFirstAidToDetail(
                    categoryId   = category.id,
                    categoryName = category.name
                )
            findNavController().navigate(action)
        }
        // Do NOT set layoutManager here — fragment_first_aid.xml already declares
        // GridLayoutManager with spanCount=2 via app:layoutManager and app:spanCount
        binding.rvCategories.adapter = categoryAdapter
    }

    private fun setupSearch() {
        // Layout uses TextInputLayout + TextInputEditText — not a SearchView
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            if (query.isEmpty()) {
                viewModel.loadAllCategories()
            } else {
                viewModel.searchCategories(query)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.categoriesWithStepCount.collect { categories ->
                        categoryAdapter.submitList(categories)
                        // Correct ID: tv_empty (not tvEmptyState)
                        binding.tvEmpty.isVisible = categories.isEmpty()
                        binding.rvCategories.isVisible = categories.isNotEmpty()
                    }
                }
                // No progress_indicator in fragment_first_aid.xml — no loading observer needed
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}