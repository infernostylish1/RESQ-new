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

/**
 * Home screen fragment.
 *
 * Fixes applied:
 * - tvDailyTip → tvTipContent (matches fragment_home.xml)
 * - Removed non-existent card IDs (cardDisasterGuide, cardFirstAid, cardTips)
 * - card_emergency_contacts navigates to emergency contacts
 * - Quick access RecyclerView handles disaster navigation
 * - Uses etSearch.addTextChangedListener (layout has til_search/et_search, not SearchView)
 * - Removed .name reference on UserProfileEntity (entity has no name field)
 */
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
        setupClickListeners()
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

    private fun setupClickListeners() {
        // Emergency contacts card — the only card click in this layout
        binding.cardEmergencyContacts.setOnClickListener {
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToEmergencyContacts()
            )
        }

        // Favorite button on the daily tip card
        binding.btnFavoriteTip.setOnClickListener {
            viewModel.toggleTipFavorite()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val query = editable?.toString().orEmpty()
            viewModel.onSearchQueryChanged(query)
        }
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
                        // Show up to 5 in the quick access horizontal strip
                        quickAccessAdapter.submitList(disasters.take(5))
                    }
                }

                launch {
                    viewModel.currentTip.collect { tip ->
                        tip?.let {
                            // Correct ID from layout: tv_tip_content
                            binding.tvTipContent.text = it.tipText
                            val starIcon = if (it.isFavorite) {
                                android.R.drawable.btn_star_big_on
                            } else {
                                android.R.drawable.btn_star_big_off
                            }
                            binding.btnFavoriteTip.setImageResource(starIcon)
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