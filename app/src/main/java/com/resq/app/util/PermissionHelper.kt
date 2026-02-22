package com.resq.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for managing runtime permissions in RESQ.
 */
@Singleton
class PermissionHelper @Inject constructor() {

    companion object {
        const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
        const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO
        )
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPermission(context: Context): Boolean {
        return isPermissionGranted(context, PERMISSION_CALL_PHONE)
    }

    fun hasAudioPermission(context: Context): Boolean {
        return isPermissionGranted(context, PERMISSION_RECORD_AUDIO)
    }

    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            isPermissionGranted(context, permission)
        }
    }

    fun getMissingPermissions(context: Context): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            !isPermissionGranted(context, permission)
        }
    }

    fun getPermissionDisplayName(permission: String, languageCode: String = "en"): String {
        return when (languageCode.lowercase()) {
            "tl", "fil" -> when (permission) {
                PERMISSION_CALL_PHONE -> "Pagtawag sa Telepono"
                PERMISSION_RECORD_AUDIO -> "Pagre-record ng Audio"
                else -> permission
            }
            else -> when (permission) {
                PERMISSION_CALL_PHONE -> "Phone Calls"
                PERMISSION_RECORD_AUDIO -> "Audio Recording"
                else -> permission
            }
        }
    }

    fun getPermissionRationale(permission: String, languageCode: String = "en"): String {
        return when (languageCode.lowercase()) {
            "tl", "fil" -> when (permission) {
                PERMISSION_CALL_PHONE -> "Kailangan para tawagan ang mga emergency contact nang direkta mula sa app."
                PERMISSION_RECORD_AUDIO -> "Kailangan para sa voice command functionality."
                else -> "Kailangan ang permisyong ito para sa functionality ng app."
            }
            else -> when (permission) {
                PERMISSION_CALL_PHONE -> "Required to call emergency contacts directly from the app."
                PERMISSION_RECORD_AUDIO -> "Required for voice command functionality."
                else -> "This permission is required for app functionality."
            }
        }
    }
}