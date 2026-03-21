package com.resq.app.util

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.DrawableRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.resq.app.R
import com.resq.app.databinding.DialogConfirmationBinding

object DialogHelper {

    fun showConfirmationDialog(
        context: Context,
        @DrawableRes iconRes: Int,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String = context.getString(R.string.action_cancel),
        isPositiveDestructive: Boolean = false,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        val binding = DialogConfirmationBinding.inflate(LayoutInflater.from(context))

        binding.ivDialogIcon.setImageResource(iconRes)
        binding.tvDialogTitle.text = title
        binding.tvDialogMessage.text = message
        binding.btnNegative.text = negativeText
        binding.btnPositive.text = positiveText

        if (isPositiveDestructive) {
            binding.btnPositive.setBackgroundColor(context.getColor(R.color.error))
            binding.btnPositive.setTextColor(context.getColor(R.color.on_error))
            binding.ivDialogIcon.imageTintList =
                android.content.res.ColorStateList.valueOf(context.getColor(R.color.error))
        }

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_RESQ_Dialog)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnNegative.setOnClickListener {
            onNegative?.invoke()
            dialog.dismiss()
        }

        binding.btnPositive.setOnClickListener {
            onPositive()
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showExitConfirmation(context: Context, onConfirmExit: () -> Unit) {
        showConfirmationDialog(
            context = context,
            iconRes = R.drawable.ic_exit,
            title = context.getString(R.string.exit_dialog_title),
            message = context.getString(R.string.exit_dialog_message),
            positiveText = context.getString(R.string.action_exit),
            negativeText = context.getString(R.string.action_cancel),
            isPositiveDestructive = true,
            onPositive = onConfirmExit
        )
    }

    fun showSelectionDialog(
        context: Context,
        title: String,
        items: Array<String>,
        selectedIndex: Int,
        onItemSelected: (Int) -> Unit
    ) {
        MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_RESQ_Dialog)
            .setTitle(title)
            .setSingleChoiceItems(items, selectedIndex) { dialog, which ->
                onItemSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}