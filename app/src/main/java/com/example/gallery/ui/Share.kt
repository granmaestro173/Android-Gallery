package com.example.gallery.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.gallery.data.MediaItem
import com.example.gallery.data.MediaType

/** Sends photos/videos to WhatsApp or to any app through Android's share menu. */
object Share {

    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",     // WhatsApp
        "com.whatsapp.w4b", // WhatsApp Business
    )

    /** Opens Android's share menu (WhatsApp, Telegram, Gmail, Drive, Bluetooth, ...). */
    fun toAnyApp(context: Context, items: List<MediaItem>) {
        val intent = buildIntent(items) ?: return
        val title = if (items.size == 1) "Share via" else "Share ${items.size} items via"
        context.startActivity(Intent.createChooser(intent, title))
    }

    /** Goes straight to WhatsApp's "send to" screen; tries WhatsApp Business if regular WhatsApp isn't installed. */
    fun toWhatsApp(context: Context, items: List<MediaItem>) {
        val intent = buildIntent(items) ?: return
        for (pkg in WHATSAPP_PACKAGES) {
            try {
                context.startActivity(Intent(intent).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // not installed, try the next one
            }
        }
        Toast.makeText(context, "WhatsApp isn't installed", Toast.LENGTH_SHORT).show()
    }

    private fun buildIntent(items: List<MediaItem>): Intent? {
        if (items.isEmpty()) return null
        val uris = ArrayList<Uri>(items.map { it.uri })
        val mime = when {
            items.all { it.type == MediaType.PHOTO } -> "image/*"
            items.all { it.type == MediaType.VIDEO } -> "video/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        // Let the receiving app read the files.
        intent.type = mime
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }
}
