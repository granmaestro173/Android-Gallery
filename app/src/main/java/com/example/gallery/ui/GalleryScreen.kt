package com.example.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.gallery.data.MediaItem
import com.example.gallery.data.MediaType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onTabSelected: (GalleryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Gallery") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // --- Search bar (searches by file name) ---
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = {
                    Text(if (state.tab == GalleryTab.PHOTOS) "Search photos by name" else "Search videos by name")
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            // --- Photos / Videos separation ---
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == GalleryTab.PHOTOS,
                    onClick = { onTabSelected(GalleryTab.PHOTOS) },
                    text = { Text("Photos (${state.photoCount})") },
                    icon = { Icon(Icons.Default.Photo, null) },
                )
                Tab(
                    selected = state.tab == GalleryTab.VIDEOS,
                    onClick = { onTabSelected(GalleryTab.VIDEOS) },
                    text = { Text("Videos (${state.videoCount})") },
                    icon = { Icon(Icons.Default.Videocam, null) },
                )
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        if (state.query.isNotBlank()) "No results for \"${state.query}\""
                        else "Nothing here yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    if (state.query.isNotBlank()) {
                        Text(
                            "${state.items.size} result(s)",
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    MediaGrid(state.items, showNames = state.query.isNotBlank(), onItemClick)
                }
            }
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, showNames: Boolean, onItemClick: (MediaItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { "${it.type}-${it.id}" }) { item ->
            MediaThumb(item, showNames) { onItemClick(item) }
        }
    }
}

@Composable
private fun MediaThumb(item: MediaItem, showName: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        val request = ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .size(300)
            .apply { if (item.type == MediaType.VIDEO) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.type == MediaType.VIDEO) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(formatDuration(item.durationMs), color = Color.White, fontSize = 11.sp)
            }
        }

        if (showName) {
            Text(
                item.name,
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
