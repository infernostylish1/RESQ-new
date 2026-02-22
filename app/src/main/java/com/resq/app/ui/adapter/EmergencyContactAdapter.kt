package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.model.ContactCategory
import com.resq.app.databinding.ItemContactHeaderBinding
import com.resq.app.databinding.ItemEmergencyContactBinding

class EmergencyContactAdapter(
    private val onCallClick: (EmergencyContactEntity) -> Unit
) : ListAdapter<EmergencyContactAdapter.ContactListItem, RecyclerView.ViewHolder>(ContactDiffCallback()) {

    sealed class ContactListItem {
        data class Header(val category: ContactCategory) : ContactListItem()
        data class Contact(val contact: EmergencyContactEntity) : ContactListItem()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_CONTACT = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ContactListItem.Header -> VIEW_TYPE_HEADER
            is ContactListItem.Contact -> VIEW_TYPE_CONTACT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemContactHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemEmergencyContactBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ContactViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ContactListItem.Header -> (holder as HeaderViewHolder).bind(item.category)
            is ContactListItem.Contact -> (holder as ContactViewHolder).bind(item.contact)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemContactHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: ContactCategory) {
            binding.tvHeader.text = category.name
        }
    }

    inner class ContactViewHolder(
        private val binding: ItemEmergencyContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnCall.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is ContactListItem.Contact) {
                        onCallClick(item.contact)
                    }
                }
            }
        }

        fun bind(contact: EmergencyContactEntity) {
            binding.apply {
                tvContactName.text = contact.name
                tvContactNumber.text = contact.phoneNumber
            }
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<ContactListItem>() {
        override fun areItemsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return when {
                oldItem is ContactListItem.Header && newItem is ContactListItem.Header ->
                    oldItem.category == newItem.category
                oldItem is ContactListItem.Contact && newItem is ContactListItem.Contact ->
                    oldItem.contact.id == newItem.contact.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ContactListItem, newItem: ContactListItem): Boolean {
            return oldItem == newItem
        }
    }

    fun submitContactList(contacts: List<EmergencyContactEntity>) {
        val items = mutableListOf<ContactListItem>()
        val groupedContacts = contacts.groupBy { it.category }

        ContactCategory.entries.forEach { category ->
            val categoryContacts = groupedContacts[category]
            if (!categoryContacts.isNullOrEmpty()) {
                items.add(ContactListItem.Header(category))
                categoryContacts.forEach { contact ->
                    items.add(ContactListItem.Contact(contact))
                }
            }
        }

        submitList(items)
    }
}