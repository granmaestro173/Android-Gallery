package com.example.gallery.data

/**
 * A group of photos/videos that came from the same place.
 * [rank] controls the order of the category buttons (lower first).
 */
data class Category(val id: String, val label: String, val rank: Int)

/**
 * Works out where a photo or video came from, using its file name and the folder it is in.
 *
 * Apps and downloaders leave recognisable marks:
 *  - WhatsApp names files like `IMG-20240512-WA0007.jpg`
 *  - TikTok's own "Save video" puts videos in the Camera folder with a 32-character code as the
 *    name (e.g. `3f2a9c0b1d4e5f60718293a4b5c6d7e8.mp4`); TikTok downloaders use names like
 *    `ssstik.io_1715500000000.mp4`, `SnapTik.App_7301234567890123456.mp4` or the 19-digit video ID
 *  - Screenshots are named `Screenshot_...`, Messenger saves `received_...`, Facebook `FB_IMG_...`
 *  - Phone cameras use `IMG_20240512_101500.jpg`, `PXL_...`, `20240512_101500.jpg` in DCIM/Camera
 *
 * The order matters: clues in the file name are checked first, then app folders, and only then
 * the generic Camera/Downloads folders. That way a TikTok video saved into the Camera folder
 * still counts as TikTok. Anything that matches no rule is grouped by its folder name
 * (e.g. "Bluetooth"), so every app still gets its own category.
 */
object Categorizer {

    val CAMERA = Category("camera", "Camera", 0)
    val WHATSAPP = Category("whatsapp", "WhatsApp", 1)
    val TIKTOK = Category("tiktok", "TikTok", 2)
    val INSTAGRAM = Category("instagram", "Instagram", 3)
    val FACEBOOK = Category("facebook", "Facebook", 4)
    val MESSENGER = Category("messenger", "Messenger", 5)
    val TELEGRAM = Category("telegram", "Telegram", 6)
    val SNAPCHAT = Category("snapchat", "Snapchat", 7)
    val YOUTUBE = Category("youtube", "YouTube", 8)
    val TWITTER = Category("twitter", "X / Twitter", 9)
    val PINTEREST = Category("pinterest", "Pinterest", 10)
    val CAPCUT = Category("capcut", "CapCut", 11)
    val SIGNAL = Category("signal", "Signal", 12)
    val SCREENSHOTS = Category("screenshots", "Screenshots", 13)
    val SCREEN_RECORDINGS = Category("screen_recordings", "Screen recordings", 14)
    val DOWNLOADS = Category("downloads", "Downloads", 15)
    val EDITED = Category("edited", "Edited", 16)
    val OTHER = Category("other", "Other", 1000)

    private const val FOLDER_RANK = 100

    /**
     * Words that identify an app (or a downloader for it) in a file or folder name.
     * They're matched after removing spaces, dots, dashes and underscores, so
     * "Tik Tok", "tik_tok" and "TikTok" all match "tiktok".
     */
    private val keywords: List<Pair<Category, List<String>>> = listOf(
        WHATSAPP to listOf("whatsapp"),
        TIKTOK to listOf(
            "tiktok", "musically", "douyin",
            // popular TikTok downloaders
            "ssstik", "snaptik", "tikmate", "savetik", "musicaldown", "tikdown",
            "tiktokio", "lovetik", "ttdown", "tikwm", "tiksave", "ttsave",
        ),
        INSTAGRAM to listOf("instagram", "snapinsta", "instasave", "saveinsta", "fastsave", "instadown"),
        MESSENGER to listOf("messenger"),
        FACEBOOK to listOf("facebook", "fbdown", "fdownnet", "fbvideo"),
        TELEGRAM to listOf("telegram"),
        SNAPCHAT to listOf("snapchat"),
        YOUTUBE to listOf("youtube", "y2mate", "savefrom", "ssyoutube", "yt5s", "ytdown", "ytmp4"),
        TWITTER to listOf("twitter", "twdown", "twittervideo", "ssstwitter"),
        PINTEREST to listOf("pinterest"),
        CAPCUT to listOf("capcut"),
    )

    private val whatsAppName = Regex("""^(img|vid|aud|ptt|stk|doc)-\d{8}-wa\d+""")
    /** TikTok's own Save button: a 32-character code, sometimes followed by a number. */
    private val tikTokCodeName = Regex("""^[0-9a-f]{32}(_\d+)?$""")
    /** A 19-digit TikTok video ID (they start with 6 or 7), on its own or with a username/prefix. */
    private val tikTokIdName = Regex("""(^|[^0-9])[67]\d{18}([^0-9]|$)""")
    /** Facebook/Instagram's own download names also contain long numbers, e.g. "123_4567_890_n". */
    private val metaCdnName = Regex("""\d+_\d+_\d+_n$""")
    private val cameraName = Regex(
        """^(img|vid|pxl|dsc|dscn|dcim|mvimg|photo|video|cimg|p)[_-]?\d{8}""" + // IMG_20240512..., PXL_2024...
            """|^\d{8}_\d{6}""" +                                                 // Samsung: 20240512_101500.jpg
            """|^dsc_?\d{3,}"""                                                   // DSC_0001.jpg / DSC0001.jpg
    )
    /**
     * Screen recordings: Pixel/Samsung "Screen_recording_...", Xiaomi "Screenrecorder-...",
     * Motorola "screen-20240512-101500", Huawei "SVID_...", others "Record_screen_...".
     */
    private val screenRecordingName = Regex(
        """^(screen[_ -]?record|record[_ -]?screen|screen[_ -]?rec[_ -]|svid_|screen-\d{8}|rec_?screen)"""
    )
    private val videoExtensions = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")

    /** Lowercase, accents removed ("grabación" -> "grabacion"), only letters and digits kept. */
    private fun squash(s: String) =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("[^a-z0-9]"), "")

    /**
     * @param name   file name, e.g. "IMG-20240512-WA0007.jpg"
     * @param path   folder path, e.g. "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
     * @param folder the folder's display name, e.g. "WhatsApp Images" (may be empty)
     * @param isVideo whether it's a video; worked out from the file extension if not given
     */
    fun categorize(name: String, path: String, folder: String, isVideo: Boolean? = null): Category {
        val n = name.lowercase()
        val base = n.substringBeforeLast('.')     // name without ".jpg" / ".mp4"
        val video = isVideo ?: (n.substringAfterLast('.', "") in videoExtensions)
        val sn = squash(base)
        val p = path.lowercase().replace('\\', '/')
        val sp = squash(p) + squash(folder)

        // 0. Screen captures first, so a recording of TikTok ("Screen_Recording_..._TikTok.mp4")
        //    is a screen recording, not a TikTok video. A *video* can never be a screenshot:
        //    many phones save screen recordings in the Screenshots folder.
        val recordingByName = screenRecordingName.containsMatchIn(n) ||
            listOf("screenrecord", "recordscreen", "grabaciondepantalla").any { it in sn }
        val recordingByFolder = listOf("screenrecord", "recordscreen", "grabaciondepantalla").any { it in sp }
        val screenshotByName = n.startsWith("screenshot") || "capturadepantalla" in sn || "screencapture" in sn
        val screenshotByFolder = listOf("screenshot", "capturadepantalla", "capturas", "screencapture").any { it in sp }
        when {
            recordingByName || recordingByFolder -> return SCREEN_RECORDINGS
            screenshotByName || screenshotByFolder -> return if (video) SCREEN_RECORDINGS else SCREENSHOTS
        }

        // 1. Clear patterns in the file name.
        when {
            whatsAppName.containsMatchIn(n) -> return WHATSAPP
            n.startsWith("received_") -> return MESSENGER
            n.startsWith("fb_img") || n.startsWith("fb_vid") -> return FACEBOOK
            n.startsWith("snapchat-") -> return SNAPCHAT
            n.startsWith("signal-") -> return SIGNAL
        }
        keywords.firstOrNull { (_, words) -> words.any { squash(it) in sn || it in base } }?.let { return it.first }
        if (tikTokCodeName.matches(base)) return TIKTOK
        if (tikTokIdName.containsMatchIn(base) && !metaCdnName.containsMatchIn(base)) return TIKTOK

        // 2. App folders.
        keywords.firstOrNull { (_, words) -> words.any { squash(it) in sp } }?.let { return it.first }
        if ("signal" in sp) return SIGNAL

        // 3. Generic folders, then the folder's own name.
        return when {
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
