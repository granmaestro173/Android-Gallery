package com.example.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.gallery.data.MediaItem
import com.example.gallery.data.Category
import com.example.gallery.data.MediaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onTabSelected: (GalleryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onDateFilterChange: (DateFilter) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSetHidden: (Category, MediaType, Boolean) -> Unit,
    onShowAllCategories: () -> Unit,
    onBlocksViewChange: (Boolean) -> Unit,
    onBlockGroupingChange: (BlockGrouping) -> Unit,
    onOpenBlock: (String) -> Unit,
    onCloseBlock: () -> Unit,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Blocks view: either the tiles themselves, or one tile opened.
    val insideBlock = state.blocksView && state.openBlockId != null
    val showingBlocks = state.blocksView && !insideBlock

    // Back leaves an opened tile, and leaves selection mode first (declared last, so it wins).
    BackHandler(enabled = insideBlock && !state.selecting, onBack = onCloseBlock)
    BackHandler(enabled = state.selecting, onBack = onClearSelection)

    Scaffold(
        topBar = {
            if (state.selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, "Cancel selection") }
                    },
                    title = { Text("${state.selectedKeys.size} selected") },
                    actions = {
                        IconButton(onClick = onSelectAll) { Icon(Icons.Default.SelectAll, "Select all") }
                        IconButton(onClick = { Share.toWhatsApp(context, state.selectedItems) }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, "Send to WhatsApp")
                        }
                        IconButton(onClick = { Share.toAnyApp(context, state.selectedItems) }) {
                            Icon(Icons.Default.Share, "Share to other apps")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        if (insideBlock) {
                            IconButton(onClick = onCloseBlock) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to blocks")
                            }
                        }
                    },
                    title = {
                        Text(
                            if (insideBlock) "${state.openBlockLabel ?: ""} · ${state.resultCount}"
                            else "Gallery",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = { onBlocksViewChange(!state.blocksView) }) {
                            if (state.blocksView) Icon(Icons.Default.GridView, "Show all items")
                            else Icon(Icons.Default.Folder, "Show category blocks")
                        }
                        IconButton(onClick = { showHideDialog = true }) {
                            Icon(Icons.Default.VisibilityOff, "Hide categories")
                        }
                    },
                )
            }
        }
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

            // --- Date categories ---
            DateFilterRow(
                selected = state.dateFilter,
                onSelect = onDateFilterChange,
                onPickRange = { showRangePicker = true },
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

            // --- Categories: where the photos/videos came from (blocks view has its own tiles) ---
            if (!state.blocksView) {
                CategoryRow(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    total = if (state.tab == GalleryTab.PHOTOS) state.photoCount else state.videoCount,
                    onSelect = onCategoryChange,
                )
            }

            // --- In the blocks view: choose what the tiles are grouped by ---
            if (showingBlocks) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Group by", style = MaterialTheme.typography.labelLarge)
                    BlockGrouping.entries.forEach { g ->
                        FilterChip(
                            selected = state.blockGrouping == g,
                            onClick = { onBlockGroupingChange(g) },
                            label = { Text(g.label) },
                        )
                    }
                }
            }

            // Reminder that some categories are hidden, with a shortcut to change it.
            if (state.hiddenInTab > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.VisibilityOff, null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${state.hiddenInTab} hidden by your category settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showHideDialog = true }) { Text("Change") }
                }
            }

            val filtering = state.query.isNotBlank() ||
                state.dateFilter != DateFilter.All ||
                state.selectedCategoryId != null
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                showingBlocks -> if (state.blocks.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                        Text(
                            "Nothing matches your search and filters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    BlocksGrid(state.blocks, isVideos = state.tab == GalleryTab.VIDEOS, onOpen = onOpenBlock)
                }
                state.sections.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        if (filtering) "Nothing matches your search and filters"
                        else "Nothing here yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    if (filtering && !insideBlock) {
                        Text(
                            "${state.resultCount} result(s)",
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    MediaGrid(
                        sections = state.sections,
                        showNames = state.query.isNotBlank(),
                        selectedKeys = state.selectedKeys,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                    )
                }
            }
        }
    }

    if (showHideDialog) {
        HideCategoriesDialog(
            rows = state.allCategories,
            onSetHidden = onSetHidden,
            onShowAll = onShowAllCategories,
            onDismiss = { showHideDialog = false },
        )
    }

    if (showRangePicker) {
        DateRangeDialog(
            initial = state.dateFilter as? DateFilter.Range,
            onDismiss = { showRangePicker = false },
            onConfirm = { range ->
                onDateFilterChange(range)
                showRangePicker = false
            },
        )
    }
}

/** Tiles ("cubes"): one square per source, month or year with a preview of its newest items. */
@Composable
private fun BlocksGrid(blocks: List<Block>, isVideos: Boolean, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(blocks, key = { it.id }) { block ->
            BlockTile(block, isVideos) { onOpen(block.id) }
        }
    }
}

@Composable
private fun BlockTile(block: Block, isVideos: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        // Cover: a 2x2 mosaic when there are 4+ items, otherwise the newest one.
        if (block.covers.size >= 4) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        for (col in 0..1) {
                            CoverImage(block.covers[row * 2 + col], Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        } else {
            block.covers.firstOrNull()?.let { CoverImage(it, Modifier.fillMaxSize()) }
        }

        // Name and count on a dark fade so they're readable over any picture.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp)
        ) {
            Text(
                block.label,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${block.count} " + when {
                    isVideos -> if (block.count == 1) "video" else "videos"
                    else -> if (block.count == 1) "photo" else "photos"
                },
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }

        if (isVideos) {
            Icon(
                Icons.Default.PlayArrow, null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(22.dp),
            )
        }
    }
}

/** Thumbnail for a photo, or a frame from a video. */
@Composable
private fun CoverImage(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .size(300)
            .apply { if (item.type == MediaType.VIDEO) decoderFactory(VideoFrameDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * Lets the user hide whole categories from the gallery, separately for photos and videos.
 * A highlighted button means "shown"; tap it to hide that part of the category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HideCategoriesDialog(
    rows: List<CategoryVisibility>,
    onSetHidden: (Category, MediaType, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show or hide categories") },
        text = {
            Column {
                Text(
                    "Tap Photos or Videos to hide them from the gallery. " +
                        "Nothing is deleted — hidden items stay on your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (rows.isEmpty()) {
                    Text("No categories yet.")
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    listItems(rows, key = { it.category.id }) { row ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(row.category.label, style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (row.photoCount > 0) {
                                    VisibilityChip(
                                        label = "Photos · ${row.photoCount}",
                                        shown = !row.photosHidden,
                                        onClick = { onSetHidden(row.category, MediaType.PHOTO, !row.photosHidden) },
                                    )
                                }
                                if (row.videoCount > 0) {
                                    VisibilityChip(
                                        label = "Videos · ${row.videoCount}",
                                        shown = !row.videosHidden,
                                        onClick = { onSetHidden(row.category, MediaType.VIDEO, !row.videosHidden) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (rows.any { it.photosHidden || it.videosHidden }) {
                TextButton(onClick = onShowAll) { Text("Show all") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityChip(label: String, shown: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = shown,
        onClick = onClick,
        label = { Text(if (shown) label else "$label (hidden)") },
        leadingIcon = {
            Icon(
                if (shown) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (shown) "Shown" else "Hidden",
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    categories: List<CategoryCount>,
    selectedId: String?,
    total: Int,
    onSelect: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("All sources · $total") },
        )
        categories.forEach { (category, count) ->
            FilterChip(
                selected = selectedId == category.id,
                // Tapping the selected category again goes back to all.
                onClick = { onSelect(if (selectedId == category.id) null else category.id) },
                label = { Text("${category.label} · $count") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterRow(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    onPickRange: () -> Unit,
) {
    val presets = listOf(
        DateFilter.All to "All dates",
        DateFilter.Today to "Today",
        DateFilter.Last7Days to "Last 7 days",
        DateFilter.ThisMonth to "This month",
        DateFilter.ThisYear to "This year",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
            )
        }
        val range = selected as? DateFilter.Range
        FilterChip(
            selected = range != null,
            onClick = onPickRange,
            label = { Text(range?.let { rangeLabel(it) } ?: "Pick dates…") },
            leadingIcon = { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)) },
        )
    }
}

private val shortDate = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun rangeLabel(r: DateFilter.Range): String =
    if (r.start == r.end) r.start.format(shortDate)
    else "${r.start.format(shortDate)} – ${r.end.format(shortDate)}"

/** The Material date picker works in UTC midnight millis; convert to/from calendar days. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initial: DateFilter.Range?,
    onDismiss: () -> Unit,
    onConfirm: (DateFilter.Range) -> Unit,
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.end?.toPickerMillis(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis!!.toLocalDateUtc()
                    // A single tapped day means "just that day".
                    val end = pickerState.selectedEndDateMillis?.toLocalDateUtc() ?: start
                    onConfirm(DateFilter.Range(start, end))
                },
            ) { Text("Show") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.weight(1f),
            title = { Text("Choose a day or range", Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}

@Composable
private fun MediaGrid(
    sections: List<DateSection>,
    showNames: Boolean,
    selectedKeys: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.day}", span = { GridItemSpan(maxLineSpan) }) {
                DateHeader(section)
            }
            items(section.items, key = { it.key }) { item ->
                MediaThumb(
                    item = item,
                    showName = showNames,
                    selected = item.key in selectedKeys,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                )
            }
        }
    }
}

@Composable
private fun DateHeader(section: DateSection) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            section.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${section.items.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaThumb(
    item: MediaItem,
    showName: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val selectedColor = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.DarkGray)
            .then(if (selected) Modifier.border(3.dp, selectedColor, RoundedCornerShape(4.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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

        if (selected) {
            Box(Modifier.fillMaxSize().background(selectedColor.copy(alpha = 0.25f)))
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = selectedColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.White, CircleShape)
                    .size(22.dp),
            )
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
