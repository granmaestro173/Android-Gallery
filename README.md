# Gallery

Android gallery app (Kotlin + Jetpack Compose):

- Photos and Videos shown in separate tabs
- Search by file name (case-insensitive, words in any order)
- Grid grouped under date headings (Today, Yesterday, then each day)
- Date filters: Today, Last 7 days, This month, This year, or any day/range from a calendar
- Automatic categories by source (WhatsApp, Camera, Screenshots, Screen recordings, Telegram,
  Instagram, Facebook, Messenger, TikTok, Snapchat, X, Signal, Downloads, Edited, plus any other
  app's folder), worked out from file names and folders — see `data/Categorizer.kt`
- Share to WhatsApp or any app from the viewer; long-press in the grid to select and share several
- Full-screen viewer with pinch-to-zoom for photos and a built-in video player

Requires Android 8.0 or newer.

## Build

- **Android Studio:** File → Open → this folder, then Run.
- **GitHub Actions:** every push builds a debug APK. Download it from the
  workflow run's *Artifacts* section (`gallery-debug-apk`).
