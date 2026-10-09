package com.resq.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resq.app.R
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.databinding.ItemEmergencyContactBinding

class EmergencyContactAdapter(
    private val onCallClick: (EmergencyContactEntity) -> Unit,
    private val onEditClick: (EmergencyContactEntity) -> Unit = {},
    private val onDeleteClick: (EmergencyContactEntity) -> Unit = {}
) : ListAdapter<EmergencyContactEntity, EmergencyContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemEmergencyContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnCall.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCallClick(getItem(position))
                }
            }
            binding.btnEdit.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
            binding.btnDelete.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }

        fun bind(contact: EmergencyContactEntity) {
            binding.apply {
                tvContactName.text = contact.name
                tvContactNumber.text = contact.phoneNumber
                btnCall.contentDescription = root.context.getString(
                    R.string.cd_call_named_contact, contact.name
                )
                contactActions.visibility = if (contact.isCustom) View.VISIBLE else View.GONE
            }
        }
    }

    private class ContactDiffCallback : DiffUtil.ItemCallback<EmergencyContactEntity>() {
        override fun areItemsTheSame(
            oldItem: EmergencyContactEntity,
            newItem: EmergencyContactEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: EmergencyContactEntity,
            newItem: EmergencyContactEntity
        ): Boolean {
            return oldItem == newItem
        }
    }
}
