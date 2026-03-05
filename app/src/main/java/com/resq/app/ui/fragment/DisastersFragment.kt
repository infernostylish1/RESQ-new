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
import com.resq.app.R
import com.resq.app.databinding.FragmentDisastersBinding
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.adapter.SectionHeaderAdapter
import com.resq.app.ui.viewmodel.DisasterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Disasters list fragment.
 *
 * Changes from original:
 * - Added two-section layout via ConcatAdapter:
 *     Section 0: "For You" header + recommendedDisasters adapter
 *     Section 1: "Other Disasters" header + otherDisasters adapter
 * - Both section headers hide automatically when their list is empty
 *   (no profile, search returns nothing in that bucket, etc.)
 * - Search delegates to DisasterViewModel.searchDisasters() which
 *   re-splits results across both flows — no fragment-side filtering needed.
 * - All binding IDs unchanged (rv_disasters, et_search, tv_empty,
 *   progress_indicator) — no layout file changes required.
 */
@AndroidEntryPoint
class DisastersFragment : Fragment() {

    private var _binding: FragmentDisastersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DisasterViewModel by viewModels()

    // Section 0 — profile-matched disasters
    private lateinit var recommendedHeader: SectionHeaderAdapter
    private lateinit var recommendedAdapter: DisasterAdapter

    // Section 1 — everything else
    private lateinit var otherHeader: SectionHeaderAdapter
    private lateinit var otherAdapter: DisasterAdapter

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
        setupSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        val onItemClick: (com.resq.app.data.entity.DisasterEntity) -> Unit = { disaster ->
            val action = DisastersFragmentDirections
                .actionDisastersToDetail(
                    disasterId   = disaster.id,
                    disasterName = disaster.name
                )
            findNavController().navigate(action)
        }

        recommendedHeader  = SectionHeaderAdapter(getString(R.string.disasters_section_for_you))
        recommendedAdapter = DisasterAdapter(onItemClick)

        otherHeader  = SectionHeaderAdapter(getString(R.string.disasters_section_other))
        otherAdapter = DisasterAdapter(onItemClick)

        val concatAdapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true) // both DisasterAdapters share ViewHolder types
                .build(),
            recommendedHeader,
            recommendedAdapter,
            otherHeader,
            otherAdapter
        )

        binding.rvDisasters.adapter = concatAdapter
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
                        // Hide the "For You" header when the section is empty
                        // (no profile, or search produced no matches in this bucket).
                        recommendedHeader.setVisible(recommended.isNotEmpty())
                    }
                }

                launch {
                    viewModel.otherDisasters.collect { other ->
                        otherAdapter.submitList(other)
                        otherHeader.setVisible(other.isNotEmpty())
                    }
                }

                // Empty state: show only when BOTH sections are empty.
                launch {
                    viewModel.disasters.collect { all ->
                        val isEmpty = all.isEmpty()
                        binding.tvEmpty.isVisible      = isEmpty
                        binding.rvDisasters.isVisible  = !isEmpty
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