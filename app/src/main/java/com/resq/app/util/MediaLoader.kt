package com.resq.app.util

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import com.resq.app.data.model.MediaType

/**
 * Centralized media loader for step-card RecyclerView items.
 *
 * Handles three media types against two views inside each card_media container:
 *
 *   IMAGE → iv_step_media via Glide          from res/drawable  (jpg/png/webp)
 *   GIF   → iv_step_media via Glide .asGif() from res/drawable  (.gif)
 *   VIDEO → pv_step_media via ExoPlayer       from res/raw       (.mp4)
 *
 * Visibility contract (MediaLoader is the only authority):
 *   card_media   VISIBLE when any media is shown;  GONE otherwise.
 *   iv_step_media VISIBLE for IMAGE/GIF;            GONE for VIDEO or no media.
 *   pv_step_media VISIBLE for VIDEO;                GONE for IMAGE/GIF or no media.
 *
 * ExoPlayer lifecycle contract:
 *   bind() returns a non-null ExoPlayer when VIDEO is started.
 *   The ViewHolder must store this reference and pass it to releasePlayer()
 *   from onViewRecycled() to prevent player leaks across item recycling.
 *
 * Remote URLs:
 *   If mediaResName starts with "http://" or "https://", Glide receives the
 *   string directly and streams it. ExoPlayer also accepts http(s) URIs.
 *   No code change is needed here when you switch from local to remote assets.
 */
object MediaLoader {

    /**
     * Binds media to a card_media container.
     *
     * @param context       Fragment or Activity context.
     * @param cardMedia     The MaterialCardView with id card_media.
     * @param ivStepMedia   The ImageView  with id iv_step_media inside card_media.
     * @param pvStepMedia   The PlayerView with id pv_step_media inside card_media.
     * @param mediaResName  Drawable/raw resource name, https URL, or null/blank for no media.
     * @param mediaType     TEXT / IMAGE / GIF / VIDEO — controls which view receives content.
     * @return              An ExoPlayer instance if VIDEO was started; null for everything else.
     *                      The caller must release this value via releasePlayer().
     */
    fun bind(
        context: Context,
        cardMedia: MaterialCardView,
        ivStepMedia: ImageView,
        pvStepMedia: PlayerView,
        mediaResName: String?,
        mediaType: MediaType
    ): ExoPlayer? {

        // No media name → hide everything and bail immediately.
        if (mediaResName.isNullOrBlank()) {
            hideAll(cardMedia, ivStepMedia, pvStepMedia)
            Glide.with(context).clear(ivStepMedia)
            return null
        }

        return when (mediaType) {

            MediaType.IMAGE -> {
                val source = resolveDrawableSource(context, mediaResName)
                if (source == null) {
                    hideAll(cardMedia, ivStepMedia, pvStepMedia)
                    return null
                }
                cardMedia.visibility  = View.VISIBLE
                ivStepMedia.visibility = View.VISIBLE
                pvStepMedia.visibility = View.GONE

                Glide.with(context)
                    .load(source)
                    .apply(defaultImageOptions())
                    .into(ivStepMedia)

                null
            }

            MediaType.GIF -> {
                val source = resolveDrawableSource(context, mediaResName)
                if (source == null) {
                    hideAll(cardMedia, ivStepMedia, pvStepMedia)
                    return null
                }
                cardMedia.visibility  = View.VISIBLE
                ivStepMedia.visibility = View.VISIBLE
                pvStepMedia.visibility = View.GONE

                Glide.with(context)
                    .asGif()
                    .load(source)
                    .apply(defaultImageOptions())
                    .into(ivStepMedia)

                null
            }

            MediaType.VIDEO -> {
                val uri = resolveVideoUri(context, mediaResName)
                if (uri == null) {
                    hideAll(cardMedia, ivStepMedia, pvStepMedia)
                    return null
                }
                cardMedia.visibility  = View.VISIBLE
                ivStepMedia.visibility = View.GONE
                pvStepMedia.visibility = View.VISIBLE

                val player = ExoPlayer.Builder(context).build()
                pvStepMedia.player = player
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
                player.repeatMode   = ExoPlayer.REPEAT_MODE_ONE

                player // caller stores and releases this
            }

            else -> {
                // MediaType.TEXT and any future types produce no media card.
                hideAll(cardMedia, ivStepMedia, pvStepMedia)
                null
            }
        }
    }

    /**
     * Safely stops and releases an ExoPlayer returned by bind().
     * Safe to call with null — no-op in that case.
     * Call this from ViewHolder.onViewRecycled() or onViewDetachedFromWindow().
     */
    fun releasePlayer(player: ExoPlayer?) {
        player?.stop()
        player?.release()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves a mediaResName to a Glide-compatible source for drawables.
     * Returns the https URL string as-is, resolves local names to resource IDs,
     * or returns null if no matching drawable exists.
     */
    private fun resolveDrawableSource(context: Context, mediaResName: String): Any? {
        if (mediaResName.startsWith("http://") || mediaResName.startsWith("https://")) {
            return mediaResName
        }
        val resId = context.resources.getIdentifier(
            mediaResName, "drawable", context.packageName
        )
        return if (resId != 0) resId else null
    }

    /**
     * Resolves a mediaResName to a URI for ExoPlayer video playback.
     * Returns the https URI as-is, resolves local raw resource names,
     * or returns null if no matching raw resource exists.
     */
    private fun resolveVideoUri(context: Context, mediaResName: String): Uri? {
        if (mediaResName.startsWith("http://") || mediaResName.startsWith("https://")) {
            return Uri.parse(mediaResName)
        }
        val resId = context.resources.getIdentifier(
            mediaResName, "raw", context.packageName
        )
        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else null
    }

    private fun defaultImageOptions(): RequestOptions =
        RequestOptions()
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

    private fun hideAll(
        cardMedia: MaterialCardView,
        ivStepMedia: ImageView,
        pvStepMedia: PlayerView
    ) {
        cardMedia.visibility   = View.GONE
        ivStepMedia.visibility = View.GONE
        pvStepMedia.visibility = View.GONE
    }
}