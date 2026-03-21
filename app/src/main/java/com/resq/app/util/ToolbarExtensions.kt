package com.resq.app.util

import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.resq.app.R

/**
 * Global toolbar setup extension - single method for ALL fragments.
 *
 * Usage for ALL fragments:
 *   binding.toolbar.setupWithNavController(this)
 *
 * For fragments with toolbar titles, also call:
 *   binding.toolbar.setupWithNavController(this, viewModel)
 */
fun Toolbar.setupWithNavController(fragment: Fragment) {
    // Set the selector drawable for press states
    navigationIcon = fragment.requireContext().getDrawable(R.drawable.ic_back_button_selector)

    setNavigationOnClickListener { view ->
        // Add haptic feedback and visual feedback
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)

        // Animate the press effect
        animateBackButtonPress(view) {
            fragment.findNavController().navigateUp()
        }
    }
}

/**
 * Extension for fragments that have ViewModels with toolbar titles
 */
fun Toolbar.setupWithNavController(fragment: Fragment, titleProvider: TitleProvider) {
    // First setup the basic functionality
    setupWithNavController(fragment)

    // Then setup title observation
    val collapsingToolbar = findCollapsingToolbarLayout(fragment)
    if (collapsingToolbar != null) {
        ToolbarTitleManager.setupToolbarTitle(fragment, collapsingToolbar, titleProvider)
    }
}

/**
 * Animates the back button press with a scale effect and color change
 */
private fun animateBackButtonPress(view: View, onComplete: () -> Unit) {
    // Scale down animation
    view.animate()
        .scaleX(0.9f)
        .scaleY(0.9f)
        .setDuration(100)
        .withEndAction {
            // Scale back up and navigate
            view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(100)
                .withEndAction {
                    onComplete()
                }
                .start()
        }
        .start()
}

private fun findCollapsingToolbarLayout(fragment: Fragment): CollapsingToolbarLayout? {
    return try {
        val view = fragment.view ?: return null
        findCollapsingToolbarRecursive(view as? android.view.ViewGroup)
    } catch (e: Exception) {
        null
    }
}

private fun findCollapsingToolbarRecursive(viewGroup: android.view.ViewGroup?): CollapsingToolbarLayout? {
    if (viewGroup == null) return null

    for (i in 0 until viewGroup.childCount) {
        val child = viewGroup.getChildAt(i)
        if (child is CollapsingToolbarLayout) {
            return child
        }
        if (child is android.view.ViewGroup) {
            val found = findCollapsingToolbarRecursive(child)
            if (found != null) return found
        }
    }
    return null
}