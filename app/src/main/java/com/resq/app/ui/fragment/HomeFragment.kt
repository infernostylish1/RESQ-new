package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.databinding.FragmentHomeBinding
import com.resq.app.ui.adapter.QuickAccessAdapter
import com.resq.app.ui.adapter.QuickFirstAidAdapter
import com.resq.app.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.resq.app.R
import com.resq.app.ui.search.GlobalSearchFragment
import com.resq.app.util.setupSettingsAction

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var quickAccessAdapter: QuickAccessAdapter
    private lateinit var quickFirstAidAdapter: QuickFirstAidAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupToolbar()
        setupMapShortcut()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        quickAccessAdapter = QuickAccessAdapter { disaster ->
            val action = HomeFragmentDirections
                .actionHomeToDisasterDetail(
                    disasterId   = disaster.id,
                    disasterName = disaster.name
                )
            findNavController().navigate(action)
        }
        binding.rvQuickAccess.adapter = quickAccessAdapter

        quickFirstAidAdapter = QuickFirstAidAdapter { category ->
            val action = HomeFragmentDirections
                .actionHomeToFirstAidDetail(
                    categoryId = category.id,
                    categoryName = category.name
                )
            findNavController().navigate(action)
        }
        binding.rvQuickFirstAid.adapter = quickFirstAidAdapter
    }

    private fun setupSearch() {
        // Home uses the empty global search field as a shortcut to the search screen.
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false
        binding.tilSearch.isEndIconVisible = false
        binding.tilSearch.setStartIconContentDescription(R.string.search_field_hint)
        binding.tilSearch.setStartIconOnClickListener { openGlobalSearch() }
        binding.tilSearch.setOnClickListener { openGlobalSearch() }
        binding.etSearch.setOnClickListener  { openGlobalSearch() }
    }

    private fun setupToolbar() {
        binding.toolbar.setupSettingsAction(this)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    findNavController().navigate(R.id.nav_settings); true
                }
                else -> false
            }
        }
    }

    private fun setupMapShortcut() {
        binding.cardMapShortcut.setOnClickListener {
            findNavController().navigate(R.id.nav_map)
        }
    }

    private fun openGlobalSearch(query: String = "") {
        val args = Bundle().apply {
            putString(
                GlobalSearchFragment.ARG_INITIAL_QUERY,
                query
            )
        }
        findNavController().navigate(R.id.action_global_search, args)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.disasters.collect { disasters ->
                        quickAccessAdapter.submitList(disasters.take(5))
                    }
                }

                launch {
                    viewModel.firstAidCategories.collect { categories ->
                        quickFirstAidAdapter.submitList(categories)
                    }
                }

                launch {
                    viewModel.currentTip.collect { tip ->
                        tip?.let {
                            val language = viewModel.getLanguageCode()
                            binding.tvTipContent.text = if (language == "tl" || language == "fil") {
                                it.tipTextTagalog
                            } else {
                                it.tipText
                            }
                        }
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
