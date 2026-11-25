package com.example.gallery.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.gallery.data.MediaItem
import com.example.gallery.data.MediaType

/** Full-screen viewer: pinch-to-zoom for photos, built-in player for videos, share buttons on top. */
@Composable
fun ViewerScreen(item: MediaItem, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (item.type) {
            MediaType.PHOTO -> ZoomableImage(item)
            MediaType.VIDEO -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(item.uri)
                        setOnPreparedListener { start() }
                    }
                },
                onRelease = { it.stopPlayback() },
            )
        }

        // Top bar with back button + file name
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (item.dateIsTaken) "Taken " else "Saved ") +
                        item.day.format(fullDateFormat).replaceFirstChar { it.uppercase() } +
                        " · " + item.category.label,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { Share.toWhatsApp(context, listOf(item)) }) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("WhatsApp", color = Color.White)
            }
            IconButton(onClick = { Share.toAnyApp(context, listOf(item)) }) {
                Icon(Icons.Default.Share, "Share to other apps", tint = Color.White)
            }
        }
    }
}

private val fullDateFormat =
    java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", java.util.Locale.getDefault())

@Composable
private fun ZoomableImage(item: MediaItem) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    AsyncImage(
        model = item.uri,
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f; offsetY = 0f
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale, scaleY = scale,
                translationX = offsetX, translationY = offsetY,
            ),
    )
}
