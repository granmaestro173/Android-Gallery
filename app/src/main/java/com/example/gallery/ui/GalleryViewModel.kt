package com.example.gallery.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gallery.data.MediaItem
import com.example.gallery.data.MediaRepository
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

private data class Filters(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val query: String = "",
    val date: DateFilter = DateFilter.All,
)

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val query: String = "",
    val dateFilter: DateFilter = DateFilter.All,
    val sections: List<DateSection> = emptyList(),
    val resultCount: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val loading: Boolean = true,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)

    private val photos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val videos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val filters = MutableStateFlow(Filters())
    private val loading = MutableStateFlow(true)

    val state: StateFlow<GalleryUiState> =
        combine(photos, videos, filters, loading) { p, v, f, l ->
            val today = LocalDate.now()
            val source = if (f.tab == GalleryTab.PHOTOS) p else v
            val matching = filterByName(source, f.query).filter { f.date.matches(it.day, today) }
            GalleryUiState(
                tab = f.tab,
                query = f.query,
                dateFilter = f.date,
                sections = groupByDay(matching, today),
                resultCount = matching.size,
                photoCount = p.size,
                videoCount = v.size,
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

    fun selectTab(t: GalleryTab) = filters.update { it.copy(tab = t) }
    fun setQuery(q: String) = filters.update { it.copy(query = q) }
    fun setDateFilter(d: DateFilter) = filters.update { it.copy(date = d) }

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
