package com.resq.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for RESQ.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class RESQApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Application-level initialization will be added here later
    }
}