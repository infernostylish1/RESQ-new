package com.resq.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for phone dialer operations in RESQ.
 */
@Singleton
class PhoneDialerHelper @Inject constructor() {

    fun openDialer(context: Context, phoneNumber: String) {
        try {
            val cleanNumber = cleanPhoneNumber(phoneNumber)
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open phone dialer", Toast.LENGTH_SHORT).show()
        }
    }

    fun makeCall(context: Context, phoneNumber: String): Boolean {
        return try {
            val cleanNumber = cleanPhoneNumber(phoneNumber)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: SecurityException) {
            openDialer(context, phoneNumber)
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to make phone call", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun cleanPhoneNumber(phoneNumber: String): String {
        return phoneNumber.filter { it.isDigit() || it == '+' }
    }

    fun formatPhoneNumber(phoneNumber: String): String {
        val cleaned = cleanPhoneNumber(phoneNumber)

        return when {
            cleaned.length <= 3 -> cleaned
            cleaned.length <= 7 -> cleaned
            cleaned.startsWith("+63") -> {
                "+63 ${cleaned.substring(3).chunked(3).joinToString(" ")}"
            }
            cleaned.startsWith("63") && cleaned.length >= 11 -> {
                "+63 ${cleaned.substring(2).chunked(3).joinToString(" ")}"
            }
            cleaned.startsWith("0") && cleaned.length >= 10 -> {
                "${cleaned.take(4)} ${cleaned.drop(4).chunked(3).joinToString(" ")}"
            }
            else -> cleaned
        }
    }

    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleaned = cleanPhoneNumber(phoneNumber)
        return cleaned.isNotEmpty() && cleaned.length >= 3 && cleaned.length <= 15
    }

    fun canMakePhoneCalls(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_DIAL)
        return intent.resolveActivity(context.packageManager) != null
    }
}