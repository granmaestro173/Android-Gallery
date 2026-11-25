package com.example.gallery.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * Reads photos and videos from the device's MediaStore.
 * Photos and videos come from separate collections, so they're naturally separated.
 * Results are sorted newest first by the date the photo/video was taken.
 */
class MediaRepository(private val context: Context) {

    suspend fun loadPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection: Uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        query(
            collection = collection,
            type = MediaType.PHOTO,
            dateTakenColumn = MediaStore.Images.ImageColumns.DATE_TAKEN,
            durationColumn = null,
        )
    }

    suspend fun loadVideos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection: Uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        query(
            collection = collection,
            type = MediaType.VIDEO,
            dateTakenColumn = MediaStore.Video.VideoColumns.DATE_TAKEN,
            durationColumn = MediaStore.Video.VideoColumns.DURATION,
        )
    }

    private fun query(
        collection: Uri,
        type: MediaType,
        dateTakenColumn: String,
        durationColumn: String?,
    ): List<MediaItem> {
        val projection = listOfNotNull(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            dateTakenColumn,
            durationColumn,
            BUCKET_COLUMN,
            PATH_COLUMN,
        ).toTypedArray()

        val zone = ZoneId.systemDefault()
        val items = mutableListOf<MediaItem>()

        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(dateTakenColumn)
            val durCol = durationColumn?.let { c.getColumnIndexOrThrow(it) }
            val bucketCol = c.getColumnIndexOrThrow(BUCKET_COLUMN)
            val pathCol = c.getColumnIndexOrThrow(PATH_COLUMN)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val takenAt = takenAtMs(c, takenCol, addedCol)
                val hasTakenDate = !c.isNull(takenCol) && c.getLong(takenCol) > 0
                val name = c.getString(nameCol) ?: ""
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = name,
                    type = type,
                    takenAtMs = takenAt,
                    dateIsTaken = hasTakenDate,
                    day = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate(),
                    sizeBytes = c.getLong(sizeCol),
                    durationMs = durCol?.let { c.getLong(it) } ?: 0L,
                    category = Categorizer.categorize(
                        name = name,
                        path = c.getString(pathCol) ?: "",
                        folder = c.getString(bucketCol) ?: "",
                        isVideo = type == MediaType.VIDEO,
                    ),
                )
            }
        }
        items.sortByDescending { it.takenAtMs }
        return items
    }

    private companion object {
        /** Display name of the folder the file is in, e.g. "WhatsApp Images" (same column for photos and videos). */
        const val BUCKET_COLUMN = MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME

        /** Folder path: RELATIVE_PATH on Android 10+, full file path on older versions. */
        @Suppress("DEPRECATION")
        val PATH_COLUMN: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH
            else MediaStore.MediaColumns.DATA
    }

    /** DATE_TAKEN is in milliseconds but can be missing; DATE_ADDED is in seconds. */
    private fun takenAtMs(c: Cursor, takenCol: Int, addedCol: Int): Long {
        val taken = if (c.isNull(takenCol)) 0L else c.getLong(takenCol)
        return if (taken > 0) taken else c.getLong(addedCol) * 1000L
    }
}
