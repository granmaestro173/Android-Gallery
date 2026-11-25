package com.example.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    private fun cat(name: String, path: String = "", folder: String = "") =
        Categorizer.categorize(name, path, folder)

    @Test fun whatsAppByName() {
        assertEquals(Categorizer.WHATSAPP, cat("IMG-20240512-WA0007.jpg", "Pictures/"))
        assertEquals(Categorizer.WHATSAPP, cat("VID-20231101-WA0012.mp4"))
    }

    @Test fun whatsAppByFolder() {
        assertEquals(
            Categorizer.WHATSAPP,
            cat("abc.jpg", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "WhatsApp Images"),
        )
    }

    @Test fun camera() {
        assertEquals(Categorizer.CAMERA, cat("IMG_20240512_101500.jpg", "DCIM/Camera/", "Camera"))
        assertEquals(Categorizer.CAMERA, cat("PXL_20240512_101500123.jpg", "DCIM/Camera/"))
        assertEquals(Categorizer.CAMERA, cat("20240512_101500.jpg", "DCIM/Camera/"))
        assertEquals(Categorizer.CAMERA, cat("VID_20240512_101500.mp4"))
        assertEquals(Categorizer.CAMERA, cat("DSC_0042.JPG", "DCIM/100NIKON/"))
    }

    @Test fun screenshotsAndRecordings() {
        assertEquals(Categorizer.SCREENSHOTS, cat("Screenshot_20240512-101500_Chrome.jpg", "Pictures/Screenshots/"))
        assertEquals(Categorizer.SCREENSHOTS, cat("Screenshot 2024-05-12.png", "DCIM/"))
        assertEquals(Categorizer.SCREEN_RECORDINGS, cat("Screen_Recording_20240512.mp4", "Movies/"))
        assertEquals(Categorizer.SCREEN_RECORDINGS, cat("video.mp4", "DCIM/Screen recordings/"))
    }

    @Test fun socialApps() {
        assertEquals(Categorizer.TELEGRAM, cat("photo_2024.jpg", "Pictures/Telegram/"))
        assertEquals(Categorizer.INSTAGRAM, cat("123.jpg", "Pictures/Instagram/"))
        assertEquals(Categorizer.MESSENGER, cat("received_1234567890.jpeg", "Pictures/"))
        assertEquals(Categorizer.FACEBOOK, cat("FB_IMG_1715500000000.jpg", "Pictures/"))
        assertEquals(Categorizer.SNAPCHAT, cat("Snapchat-123456.jpg", "Pictures/"))
        assertEquals(Categorizer.TIKTOK, cat("v09044.mp4", "Movies/TikTok/"))
    }

    @Test fun downloadsAndEdited() {
        assertEquals(Categorizer.DOWNLOADS, cat("cat.png", "Download/"))
        assertEquals(Categorizer.EDITED, cat("photo.jpg", "Pictures/Edited/"))
    }

    @Test fun unknownSourceGroupsByFolder() {
        val c = cat("file.jpg", "Pictures/Pinterest/", "Pinterest")
        assertEquals("Pinterest", c.label)
        assertEquals(c, cat("other.jpg", "Pictures/Pinterest/", "pinterest"))
    }

    @Test fun nothingKnown() {
        assertEquals(Categorizer.OTHER, cat("file.jpg", "", ""))
    }
}
