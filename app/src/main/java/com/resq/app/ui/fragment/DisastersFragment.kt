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
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.tabs.TabLayout
import com.resq.app.R
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.databinding.FragmentDisastersBinding
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.adapter.SectionHeaderAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DisastersFragment : Fragment() {

    private var _binding: FragmentDisastersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterViewModel by viewModels()

    private lateinit var recommendedHeader: SectionHeaderAdapter
    private lateinit var recommendedAdapter: DisasterAdapter
    private lateinit var otherHeader: SectionHeaderAdapter
    private lateinit var otherAdapter: DisasterAdapter
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
    }

    private fun setupRecyclerView() {
        val onItemClick: (DisasterEntity) -> Unit = { disaster ->
            val action = DisastersFragmentDirections.actionDisastersToDetail(
                disasterId   = disaster.id,
                disasterName = disaster.name
            )
            findNavController().navigate(action)
        }

        recommendedHeader  = SectionHeaderAdapter(getString(R.string.disasters_section_for_you))
        recommendedAdapter = DisasterAdapter(onItemClick)
        otherHeader        = SectionHeaderAdapter(getString(R.string.disasters_section_other))
        otherAdapter       = DisasterAdapter(onItemClick)
        allAdapter         = DisasterAdapter(onItemClick)

        showRecommendedTab()
    }

    private fun showRecommendedTab() {
        isRecommendedTab = true
        val concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder().setIsolateViewTypes(true).build(),
            recommendedHeader,
            recommendedAdapter,
            otherHeader,
            otherAdapter
        )
        binding.rvDisasters.adapter = concatAdapter
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
                        viewModel.loadAllDisasters()
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
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty().trim()
            if (query.isEmpty()) {
                viewModel.loadAllDisasters()
            } else {
                viewModel.searchDisasters(query)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.recommendedDisasters.collect { recommended ->
                        recommendedAdapter.submitList(recommended)
                        recommendedHeader.setVisible(recommended.isNotEmpty())
                    }
                }

                launch {
                    viewModel.otherDisasters.collect { other ->
                        otherAdapter.submitList(other)
                        otherHeader.setVisible(other.isNotEmpty())
                    }
                }

                launch {
                    viewModel.disasters.collect { all ->
                        allAdapter.submitList(all)
                        val isEmpty = all.isEmpty()
                        binding.tvEmpty.isVisible     = isEmpty
                        binding.rvDisasters.isVisible = !isEmpty
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}