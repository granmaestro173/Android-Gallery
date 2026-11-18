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
import kotlinx.coroutines.launch

enum class GalleryTab { PHOTOS, VIDEOS }

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val loading: Boolean = true,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)

    private val photos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val videos = MutableStateFlow<List<MediaItem>>(emptyList())
    private val tab = MutableStateFlow(GalleryTab.PHOTOS)
    private val query = MutableStateFlow("")
    private val loading = MutableStateFlow(true)

    val state: StateFlow<GalleryUiState> =
        combine(photos, videos, tab, query, loading) { p, v, t, q, l ->
            val source = if (t == GalleryTab.PHOTOS) p else v
            GalleryUiState(
                tab = t,
                query = q,
                items = filterByName(source, q),
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

    fun selectTab(t: GalleryTab) { tab.value = t }
    fun setQuery(q: String) { query.value = q }

    /**
     * Name search: case-insensitive, matches every word typed in any order.
     * "beach 2024" matches "IMG_2024_Beach.jpg". Underscores, dashes and dots
     * are treated as spaces so partial words match naturally.
     */
    private fun filterByName(items: List<MediaItem>, q: String): List<MediaItem> {
        val terms = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return items
        return items.filter { item ->
            val name = item.name.lowercase().replace(Regex("[_\\-.]"), " ")
            terms.all { name.contains(it) || item.name.lowercase().contains(it) }
        }
    }
}
