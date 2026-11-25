package com.example.gallery.data

import android.net.Uri
import java.time.LocalDate

enum class MediaType { PHOTO, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,          // file display name, e.g. "IMG_2024_beach.jpg"
    val type: MediaType,
    val takenAtMs: Long,       // when the photo/video was taken (falls back to when it was added)
    val day: LocalDate,        // takenAtMs as a calendar day in the phone's time zone
    val sizeBytes: Long,
    val durationMs: Long = 0L, // videos only
    val category: Category = Categorizer.OTHER, // where it came from (WhatsApp, Camera, ...)
    val dateIsTaken: Boolean = true, // true: takenAtMs is when it was taken; false: when it was saved/downloaded
)
