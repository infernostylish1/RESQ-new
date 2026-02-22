package com.resq.app.util

import android.util.TypedValue
import android.widget.TextView
import com.resq.app.data.model.FontSize
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages font size settings for RESQ.
 * Provides accessibility through adjustable text sizes.
 */
@Singleton
class FontSizeManager @Inject constructor() {

    fun getScaleFactor(fontSize: FontSize): Float {
        return when (fontSize) {
            FontSize.SMALL -> 0.85f
            FontSize.MEDIUM -> 1.0f
            FontSize.LARGE -> 1.15f
            FontSize.EXTRA_LARGE -> 1.3f
        }
    }

    fun applyToTextView(textView: TextView, fontSize: FontSize, baseSize: Float) {
        val scaledSize = baseSize * getScaleFactor(fontSize)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize)
    }

    fun getScaledSize(baseSize: Float, fontSize: FontSize): Float {
        return baseSize * getScaleFactor(fontSize)
    }

    fun getDisplayName(fontSize: FontSize, languageCode: String = "en"): String {
        return when (languageCode.lowercase()) {
            "tl", "fil" -> when (fontSize) {
                FontSize.SMALL -> "Maliit"
                FontSize.MEDIUM -> "Katamtaman"
                FontSize.LARGE -> "Malaki"
                FontSize.EXTRA_LARGE -> "Sobrang Laki"
            }
            else -> when (fontSize) {
                FontSize.SMALL -> "Small"
                FontSize.MEDIUM -> "Medium"
                FontSize.LARGE -> "Large"
                FontSize.EXTRA_LARGE -> "Extra Large"
            }
        }
    }

    fun getPreviewSize(fontSize: FontSize): Float {
        return when (fontSize) {
            FontSize.SMALL -> 14f
            FontSize.MEDIUM -> 16f
            FontSize.LARGE -> 18f
            FontSize.EXTRA_LARGE -> 22f
        }
    }

    companion object {
        const val BASE_TITLE_SIZE = 24f
        const val BASE_SUBTITLE_SIZE = 18f
        const val BASE_BODY_SIZE = 16f
        const val BASE_CAPTION_SIZE = 14f
        const val BASE_BUTTON_SIZE = 14f
    }
}