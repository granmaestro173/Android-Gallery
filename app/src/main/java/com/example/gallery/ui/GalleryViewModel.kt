package com.example.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gallery.data.Category
import com.example.gallery.data.HiddenCategories
import com.example.gallery.data.MediaItem
import com.example.gallery.data.MediaRepository
import com.example.gallery.data.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class GalleryTab { PHOTOS, VIDEOS }

/** Date categories the user can filter by. */
sealed interface DateFilter {
    data object All : DateFilter
    data object Today : DateFilter
    data object Last7Days : DateFilter
    data object ThisMonth : DateFilter
    data object ThisYear : DateFilter
    /** Inclusive range of calendar days, chosen in the date picker. */
    data class Range(val start: LocalDate, val end: LocalDate) : DateFilter

    fun matches(day: LocalDate, today: LocalDate): Boolean = when (this) {
        All -> true
        Today -> day == today
        Last7Days -> !day.isBefore(today.minusDays(6)) && !day.isAfter(today)
        ThisMonth -> day.year == today.year && day.month == today.month
        ThisYear -> day.year == today.year
        is Range -> !day.isBefore(start) && !day.isAfter(end)
    }
}

/** One date heading in the grid and the photos/videos taken that day. */
data class DateSection(val day: LocalDate, val label: String, val items: List<MediaItem>)

/** A category button: the category and how many items in the current tab belong to it. */
data class CategoryCount(val category: Category, val count: Int)

private data class Filters(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val query: String = "",
    val date: DateFilter = DateFilter.All,
    val categoryId: String? = null, // null = all categories
)

/**
 * One row of the "Hide categories" screen: a category, how many photos and videos it has,
 * and whether each is currently hidden.
 */
data class CategoryVisibility(
    val category: Category,
    val photoCount: Int,
    val videoCount: Int,
    val photosHidden: Boolean,
    val videosHidden: Boolean,
)

/** Stable key for an item, used for selection and grid keys. */
val MediaItem.key: String get() = "${type}-${id}"

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val query: String = "",
    val dateFilter: DateFilter = DateFilter.All,
    val categories: List<CategoryCount> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedKeys: Set<String> = emptySet(),
    val sections: List<DateSection> = emptyList(),
    val resultCount: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val hiddenInTab: Int = 0,                                  // items hidden in the current tab
    val allCategories: List<CategoryVisibility> = emptyList(), // for the "Hide categories" screen
    val loading: Boolean = true,
) {
    val selecting: Boolean get() = selectedKeys.isNotEmpty()
    val visibleItems: List<MediaItem> get() = sections.flatMap { it.items }
    val selectedItems: List<MediaItem> get() = visibleItems.filter { it.key in selectedKeys }
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)

    private val photos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val videos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val filters = MutableStateFlow(Filters())
    private val loading = MutableStateFlow(true)
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    private val hiddenStore = HiddenCategories(app)
    private val hidden = MutableStateFlow(hiddenStore.load())
    private val filtersAndHidden = combine(filters, hidden) { f, h -> f to h }

    val state: StateFlow<GalleryUiState> =
        combine(photos, videos, filtersAndHidden, loading, selection) { allPhotos, allVideos, (f, h), l, sel ->
            val today = LocalDate.now()
            // Hidden categories are removed before anything else, so they never show up.
            val p = allPhotos.filter { HiddenCategories.keyOf(MediaType.PHOTO, it.category.id) !in h }
            val v = allVideos.filter { HiddenCategories.keyOf(MediaType.VIDEO, it.category.id) !in h }
            val source = if (f.tab == GalleryTab.PHOTOS) p else v
            val hiddenInTab = if (f.tab == GalleryTab.PHOTOS) allPhotos.size - p.size else allVideos.size - v.size
            val matching = filterByName(source, f.query)
                .filter { f.date.matches(it.day, today) }
                .filter { f.categoryId == null || it.category.id == f.categoryId }
            GalleryUiState(
                tab = f.tab,
                query = f.query,
                dateFilter = f.date,
                categories = countCategories(source),
                selectedCategoryId = f.categoryId,
                selectedKeys = sel,
                sections = groupByDay(matching, today),
                resultCount = matching.size,
                photoCount = p.size,
                videoCount = v.size,
                hiddenInTab = hiddenInTab,
                allCategories = categoryVisibility(allPhotos, allVideos, h),
                loading = l,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun load() {
        viewModelScope.launch {
            loading.value = true
            photos.value = repo.loadPhotos()
            videos.value = repo.loadVideos()
            loading.value = false
        }
    }

    /** Switching tabs resets the category (photo and video categories differ) and the selection. */
    fun selectTab(t: GalleryTab) {
        filters.update { it.copy(tab = t, categoryId = null) }
        selection.value = emptySet()
    }
    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setDateFilter(d: DateFilter) = filters.update { it.copy(date = d) }
    fun setCategory(id: String?) = filters.update { it.copy(categoryId = id) }

    /** Show or hide one category for photos or videos. */
    fun setHidden(category: Category, type: MediaType, hide: Boolean) {
        val key = HiddenCategories.keyOf(type, category.id)
        hidden.update { if (hide) it + key else it - key }
        hiddenStore.save(hidden.value)
        // Don't leave the screen filtered to a category that just disappeared.
        val tabType = if (filters.value.tab == GalleryTab.PHOTOS) MediaType.PHOTO else MediaType.VIDEO
        if (hide && type == tabType && filters.value.categoryId == category.id) setCategory(null)
        selection.value = emptySet()
    }

    fun showAllCategories() {
        hidden.value = emptySet()
        hiddenStore.save(emptySet())
    }

    fun toggleSelected(item: MediaItem) = selection.update { s ->
        if (item.key in s) s - item.key else s + item.key
    }
    fun selectAll() { selection.value = state.value.visibleItems.map { it.key }.toSet() }
    fun clearSelection() { selection.value = emptySet() }

    /** Categories present in the current tab: known apps first, then folders by size. */
    private fun countCategories(items: List<MediaItem>): List<CategoryCount> =
        items.groupingBy { it.category }.eachCount()
            .map { (c, n) -> CategoryCount(c, n) }
            .sortedWith(compareBy<CategoryCount> { it.category.rank }.thenByDescending { it.count })

    /** Every category found on the phone (photos and videos), for the "Hide categories" screen. */
    private fun categoryVisibility(
        photos: List<MediaItem>,
        videos: List<MediaItem>,
        hidden: Set<String>,
    ): List<CategoryVisibility> {
        val photoCounts = photos.groupingBy { it.category }.eachCount()
        val videoCounts = videos.groupingBy { it.category }.eachCount()
        return (photoCounts.keys + videoCounts.keys)
            .map { c ->
                CategoryVisibility(
                    category = c,
                    photoCount = photoCounts[c] ?: 0,
                    videoCount = videoCounts[c] ?: 0,
                    photosHidden = HiddenCategories.keyOf(MediaType.PHOTO, c.id) in hidden,
                    videosHidden = HiddenCategories.keyOf(MediaType.VIDEO, c.id) in hidden,
                )
            }
            .sortedWith(compareBy<CategoryVisibility> { it.category.rank }
                .thenByDescending { it.photoCount + it.videoCount })
    }

    /**
     * Name search: case-insensitive, matches every word typed in any order.
     * "beach 2024" matches "IMG_2024_Beach.jpg". Underscores, dashes and dots
     * are treated as spaces so partial words match naturally.
     */
    private fun filterByName(items: List<MediaItem>, q: String): List<MediaItem> {
        val terms = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return items
        return items.filter { item ->
            val lower = item.name.lowercase()
            val spaced = lower.replace(Regex("[_\\-.]"), " ")
            terms.all { spaced.contains(it) || lower.contains(it) }
        }
    }

    /** Items arrive sorted newest first, so consecutive runs share a day. */
    private fun groupByDay(items: List<MediaItem>, today: LocalDate): List<DateSection> {
        val sections = mutableListOf<DateSection>()
        var currentDay: LocalDate? = null
        var bucket = mutableListOf<MediaItem>()
        for (item in items) {
            if (item.day != currentDay) {
                currentDay?.let { sections += DateSection(it, dayLabel(it, today), bucket) }
                currentDay = item.day
                bucket = mutableListOf()
            }
            bucket += item
        }
        currentDay?.let { sections += DateSection(it, dayLabel(it, today), bucket) }
        return sections
    }

    private val sameYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val otherYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())

    private fun dayLabel(day: LocalDate, today: LocalDate): String = when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> day.format(sameYearFormat)
        else -> day.format(otherYearFormat)
    }
}
