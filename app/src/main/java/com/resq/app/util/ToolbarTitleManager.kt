package com.resq.app.util

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlinx.coroutines.launch

object ToolbarTitleManager {

    fun setupToolbarTitle(
        fragment: Fragment,
        collapsingToolbar: CollapsingToolbarLayout,
        titleProvider: TitleProvider
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            titleProvider.toolbarTitle.collect { title ->
                if (title.isNotBlank()) {
                    collapsingToolbar.title = title
                }
            }
        }
    }
}