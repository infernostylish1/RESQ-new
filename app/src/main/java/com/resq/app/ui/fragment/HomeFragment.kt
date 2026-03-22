package com.resq.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.resq.app.databinding.FragmentHomeBinding
import com.resq.app.ui.adapter.QuickAccessAdapter
import com.resq.app.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.resq.app.R

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var quickAccessAdapter: QuickAccessAdapter

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
    }

    private fun setupSearch() {
        // Tapping or typing in the search bar opens GlobalSearchFragment
        binding.etSearch.isFocusable = false
        binding.etSearch.isFocusableInTouchMode = false
        binding.tilSearch.setOnClickListener { openGlobalSearch() }
        binding.etSearch.setOnClickListener  { openGlobalSearch() }
    }

    private fun openGlobalSearch(query: String = "") {
        val args = Bundle().apply {
            putString(
                com.resq.app.ui.search.GlobalSearchFragment.ARG_INITIAL_QUERY,
                query
            )
        }
        findNavController().navigate(R.id.action_global_search, args)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.greeting.collect { greeting ->
                        binding.tvGreeting.text = greeting
                    }
                }

                launch {
                    viewModel.safetyMessage.collect { message ->
                        binding.tvSafetyMessage.text = message
                    }
                }

                launch {
                    viewModel.disasters.collect { disasters ->
                        quickAccessAdapter.submitList(disasters.take(5))
                    }
                }

                launch {
                    viewModel.currentTip.collect { tip ->
                        tip?.let {
                            binding.tvTipContent.text = it.tipText
                        }
                    }
                }

                launch {
                    viewModel.userProfile.collect { profile ->
                        profile?.let {
                            binding.tvGreeting.text = viewModel.greeting.value
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