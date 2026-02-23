package com.resq.app.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.databinding.FragmentEmergencyContactsBinding
import com.resq.app.ui.adapter.EmergencyContactAdapter
import com.resq.app.ui.viewmodel.EmergencyContactsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EmergencyContactsFragment : Fragment() {

    private var _binding: FragmentEmergencyContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmergencyContactsViewModel by viewModels()

    private lateinit var contactAdapter: EmergencyContactAdapter

    private var pendingCallContact: EmergencyContactEntity? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCallContact?.let { makePhoneCall(it) }
        } else {
            showMessage("Phone permission is required to make calls")
        }
        pendingCallContact = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        contactAdapter = EmergencyContactAdapter(
            onCallClick = { contact ->
                handleCallClick(contact)
            }
        )

        binding.rvContacts.apply {
            adapter = contactAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            viewModel.searchContacts(text?.toString() ?: "")
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update adapter with filtered contacts
                    contactAdapter.submitContactList(state.filteredContacts)

                    // Handle errors
                    state.error?.let { error ->
                        showMessage(error)
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun handleCallClick(contact: EmergencyContactEntity) {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> {
                makePhoneCall(contact)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE) -> {
                showPermissionRationale(contact)
            }
            else -> {
                pendingCallContact = contact
                requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    private fun makePhoneCall(contact: EmergencyContactEntity) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            // Fall back to dial intent if call permission is revoked
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
            }
            startActivity(dialIntent)
        } catch (e: Exception) {
            showMessage("Unable to make call: ${e.message}")
        }
    }

    private fun showPermissionRationale(contact: EmergencyContactEntity) {
        Snackbar.make(
            binding.root,
            "Phone permission is needed to make emergency calls",
            Snackbar.LENGTH_LONG
        ).setAction("Grant") {
            pendingCallContact = contact
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }.show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}