package com.example.gallery.data

/**
 * A group of photos/videos that came from the same place.
 * [rank] controls the order of the category buttons (lower first).
 */
data class Category(val id: String, val label: String, val rank: Int)

/**
 * Works out where a photo or video came from, using its file name and the folder it is in.
 *
 * Apps leave recognisable marks:
 *  - WhatsApp names files like `IMG-20240512-WA0007.jpg` and saves them under a "WhatsApp" folder
 *  - Screenshots are named `Screenshot_...` and live in a "Screenshots" folder
 *  - Phone cameras use `IMG_20240512_101500.jpg`, `PXL_...`, `20240512_101500.jpg` in DCIM/Camera
 *  - Messenger saves `received_123....jpeg`, Facebook `FB_IMG_...`, Snapchat `Snapchat-...`
 *
 * Rules are checked in order and the first match wins. Anything that matches no rule is grouped
 * by its folder name (e.g. "Bluetooth", "Pinterest"), so every app still gets its own category.
 */
object Categorizer {

    val CAMERA = Category("camera", "Camera", 0)
    val WHATSAPP = Category("whatsapp", "WhatsApp", 1)
    val SCREENSHOTS = Category("screenshots", "Screenshots", 2)
    val SCREEN_RECORDINGS = Category("screen_recordings", "Screen recordings", 3)
    val TELEGRAM = Category("telegram", "Telegram", 4)
    val INSTAGRAM = Category("instagram", "Instagram", 5)
    val FACEBOOK = Category("facebook", "Facebook", 6)
    val MESSENGER = Category("messenger", "Messenger", 7)
    val TIKTOK = Category("tiktok", "TikTok", 8)
    val SNAPCHAT = Category("snapchat", "Snapchat", 9)
    val TWITTER = Category("twitter", "X / Twitter", 10)
    val SIGNAL = Category("signal", "Signal", 11)
    val DOWNLOADS = Category("downloads", "Downloads", 12)
    val EDITED = Category("edited", "Edited", 13)
    val OTHER = Category("other", "Other", 1000)

    private const val FOLDER_RANK = 100

    private val whatsAppName = Regex("""^(img|vid|aud|ptt|stk|doc)-\d{8}-wa\d+""")
    private val cameraName = Regex(
        """^(img|vid|pxl|dsc|dscn|dcim|mvimg|photo|video|cimg|p)[_-]?\d{8}""" + // IMG_20240512..., PXL_2024...
            """|^\d{8}_\d{6}""" +                                                 // Samsung: 20240512_101500.jpg
            """|^dsc_?\d{3,}"""                                                   // DSC_0001.jpg / DSC0001.jpg
    )
    private val screenRecordingName = Regex("""^(screen[_ -]?record|screenrecord|record[_ -]?screen|screen[_ -]?capture)""")

    /**
     * @param name   file name, e.g. "IMG-20240512-WA0007.jpg"
     * @param path   folder path, e.g. "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
     * @param folder the folder's display name, e.g. "WhatsApp Images" (may be empty)
     */
    fun categorize(name: String, path: String, folder: String): Category {
        val n = name.lowercase()
        val p = path.lowercase().replace('\\', '/')

        return when {
            whatsAppName.containsMatchIn(n) || "whatsapp" in p || "com.whatsapp" in p -> WHATSAPP
            "telegram" in p || "org.telegram" in p -> TELEGRAM
            screenRecordingName.containsMatchIn(n) || "screenrecord" in p.replace(" ", "") -> SCREEN_RECORDINGS
            n.startsWith("screenshot") || "screenshot" in p -> SCREENSHOTS
            "instagram" in p || "instagram" in n -> INSTAGRAM
            "messenger" in p || n.startsWith("received_") -> MESSENGER
            "facebook" in p || n.startsWith("fb_img") || n.startsWith("fb_vid") -> FACEBOOK
            "tiktok" in p || "tiktok" in n || "musically" in p -> TIKTOK
            "snapchat" in p || n.startsWith("snapchat-") -> SNAPCHAT
            "twitter" in p || n.startsWith("twitter_") -> TWITTER
            "signal" in p || n.startsWith("signal-") -> SIGNAL
            "dcim/camera" in p || cameraName.containsMatchIn(n) -> CAMERA
            "edited" in p || n.contains("_edited") || n.contains("-edited") -> EDITED
            "download" in p -> DOWNLOADS
            folder.isNotBlank() -> folderCategory(folder)
            else -> OTHER
        }
    }

    private fun folderCategory(folder: String): Category {
        val label = folder.trim().replaceFirstChar { it.uppercase() }
        return Category("folder:" + label.lowercase(), label, FOLDER_RANK)
    }
}
