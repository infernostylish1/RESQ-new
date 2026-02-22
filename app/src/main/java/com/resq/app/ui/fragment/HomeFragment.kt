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
import androidx.recyclerview.widget.LinearLayoutManager
import com.resq.app.R
import com.resq.app.databinding.FragmentHomeBinding
import com.resq.app.ui.adapter.QuickAccessAdapter
import com.resq.app.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        quickAccessAdapter = QuickAccessAdapter { disaster ->
            val action = HomeFragmentDirections.actionHomeToDisasterDetail(
                disasterId = disaster.id.toInt(),
                disasterName = disaster.name
            )
            findNavController().navigate(action)
        }

        binding.rvQuickAccess.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = quickAccessAdapter
        }
    }

    private fun setupClickListeners() {
        binding.cardEmergencyContacts.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_emergency_contacts)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        // Update disasters list
                        quickAccessAdapter.submitList(state.disasters.take(5))

                        // Update greeting (no name available in profile)
                        binding.tvGreeting.text = state.greeting

                        // Update daily tip
                        state.currentTip?.let { tip ->
                            binding.tvTipContent.text = tip.tipText
                        }

                        // Update safety message
                        binding.tvSafetyMessage.text = state.safetyMessage
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