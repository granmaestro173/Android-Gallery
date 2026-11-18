package com.example.gallery.data

import android.net.Uri

enum class MediaType { PHOTO, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,          // file display name, e.g. "IMG_2024_beach.jpg"
    val type: MediaType,
    val dateAdded: Long,       // seconds since epoch
    val sizeBytes: Long,
    val durationMs: Long = 0L, // videos only
)
