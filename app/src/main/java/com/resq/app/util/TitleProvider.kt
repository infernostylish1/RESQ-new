package com.resq.app.util

import kotlinx.coroutines.flow.StateFlow

interface TitleProvider {
    val toolbarTitle: StateFlow<String>
}