package com.resq.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre

/**
 * Main Application class for RESQ.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class RESQApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // MapLibre must be initialized before any MapView is inflated.
        // Doing it here guarantees it runs once, before any Activity/Fragment.
        MapLibre.getInstance(this)
    }
}