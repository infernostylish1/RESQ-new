package com.resq.app.ui.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.resq.app.MainActivity
import com.resq.app.R
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.databinding.FragmentEmergencyContactsBinding
import com.resq.app.ui.adapter.EmergencyContactAdapter
import com.resq.app.ui.viewmodel.EmergencyContactsViewModel
import com.resq.app.util.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.core.os.bundleOf

@AndroidEntryPoint
class EmergencyContactsFragment : Fragment() {

    private var _binding: FragmentEmergencyContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmergencyContactsViewModel by viewModels()

    private lateinit var contactAdapter: EmergencyContactAdapter

    private var pendingCallContact: EmergencyContactEntity? = null

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCallContact?.let { makePhoneCall(it) }
        } else {
            showMessage(getString(R.string.permission_denied))
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
        setupTabs()
        setupRecyclerView()
        setupSearch()
        setupAddContactListener()
        observeViewModel()

        notifyTabState(binding.tabLayout.selectedTabPosition)
    }

    private fun setupToolbar() {
        binding.toolbar.setupWithNavController(this)
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                viewModel.selectTab(position)
                notifyTabState(position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun notifyTabState(tabPosition: Int) {
        requireActivity().supportFragmentManager.setFragmentResult(
            RESULT_CONTACTS_TAB,
            bundleOf(KEY_SELECTED_TAB to tabPosition)
        )
    }

    private fun setupRecyclerView() {
        contactAdapter = EmergencyContactAdapter(
            onCallClick = { contact -> handleCallClick(contact) },
            onEditClick = { contact -> showAddEditDialog(contact) },
            onDeleteClick = { contact -> showDeleteConfirmation(contact) }
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

    private fun setupAddContactListener() {
        requireActivity().supportFragmentManager.setFragmentResultListener(
            MainActivity.REQUEST_ADD_CONTACT, viewLifecycleOwner
        ) { _, _ ->
            binding.tabLayout.getTabAt(1)?.select()
            showAddEditDialog(null)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressIndicator.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    contactAdapter.submitList(state.filteredContacts)

                    val isEmpty = !state.isLoading && state.filteredContacts.isEmpty()
                    binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.rvContacts.visibility = if (isEmpty) View.GONE else View.VISIBLE

                    if (isEmpty) {
                        binding.tvEmpty.text = when {
                            state.searchQuery.isNotBlank() ->
                                getString(R.string.empty_search_results)
                            state.selectedTab == 0 ->
                                getString(R.string.contacts_empty_primary)
                            else ->
                                getString(R.string.contacts_empty_custom)
                        }
                    }

                    state.error?.let { error ->
                        showMessage(error)
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun showAddEditDialog(contact: EmergencyContactEntity?) {
        val isEdit = contact != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_edit_contact, null)
        val tilName = dialogView.findViewById<TextInputLayout>(R.id.til_name)
        val tilPhone = dialogView.findViewById<TextInputLayout>(R.id.til_phone)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_name)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.et_phone)

        if (isEdit) {
            etName.setText(contact!!.name)
            etPhone.setText(contact.phoneNumber)
        }

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_RESQ_Dialog
        )
            .setTitle(
                if (isEdit) R.string.contacts_edit_title
                else R.string.contacts_add_title
            )
            .setView(dialogView)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text?.toString()?.trim().orEmpty()
                val phone = etPhone.text?.toString()?.trim().orEmpty()

                var valid = true

                if (name.isEmpty()) {
                    tilName.error = getString(R.string.contacts_name_required)
                    valid = false
                } else {
                    tilName.error = null
                }

                if (phone.isEmpty()) {
                    tilPhone.error = getString(R.string.contacts_phone_required)
                    valid = false
                } else {
                    tilPhone.error = null
                }

                if (valid) {
                    if (isEdit) {
                        viewModel.updateContact(contact!!.id, name, phone)
                    } else {
                        viewModel.addCustomContact(name, phone)
                    }
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation(contact: EmergencyContactEntity) {
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_RESQ_Dialog)
            .setTitle(R.string.contacts_delete)
            .setMessage(getString(R.string.contacts_delete_confirm, contact.name))
            .setPositiveButton(R.string.action_yes) { _, _ ->
                viewModel.deleteContact(contact)
            }
            .setNegativeButton(R.string.action_no, null)
            .show()
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
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
            }
            startActivity(dialIntent)
        } catch (e: Exception) {
            showMessage(getString(R.string.error_call_failed))
        }
    }

    private fun showPermissionRationale(contact: EmergencyContactEntity) {
        Snackbar.make(
            binding.root,
            getString(R.string.permission_phone_desc),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.permission_grant)) {
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

    companion object {
        const val RESULT_CONTACTS_TAB = "result_contacts_tab"
        const val KEY_SELECTED_TAB = "key_selected_tab"
    }
}