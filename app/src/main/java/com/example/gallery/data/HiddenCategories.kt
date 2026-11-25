package com.example.gallery.data

import android.content.Context

/**
 * Remembers which categories the user has hidden, separately for photos and videos
 * (e.g. hide WhatsApp photos but keep WhatsApp videos). Saved on the phone so it
 * survives closing the app.
 */
class HiddenCategories(context: Context) {

    private val prefs = context.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)

    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun save(hidden: Set<String>) {
        prefs.edit().putStringSet(KEY, hidden).apply()
    }

    companion object {
        private const val KEY = "hidden_categories"

        /** "PHOTO:whatsapp", "VIDEO:screenshots", "PHOTO:folder:pinterest", ... */
        fun keyOf(type: MediaType, categoryId: String) = "$type:$categoryId"
    }
}
