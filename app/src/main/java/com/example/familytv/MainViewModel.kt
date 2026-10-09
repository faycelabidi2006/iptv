package com.example.familytv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.familytv.data.Channel
import com.example.familytv.data.Playlist
import com.example.familytv.data.Prefs
import com.example.familytv.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var current by mutableStateOf<Channel?>(null); private set
    var playError by mutableStateOf(false); private set
    var unlocked by mutableStateOf<Set<String>>(emptySet()); private set

    val player: ExoPlayer = ExoPlayer.Builder(app).build()

    private fun <T> flow(f: kotlinx.coroutines.flow.Flow<T>, init: T) =
        f.stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val playlists = flow(Prefs.playlists(app), emptyList<Playlist>())
    val activeIndex = flow(Prefs.activeIndex(app), 0)
    val favorites = flow(Prefs.favorites(app), emptySet<String>())
    val hidden = flow(Prefs.hidden(app), emptySet<String>())
    val locked = flow(Prefs.locked(app), emptySet<String>())
    val recent = flow(Prefs.recent(app), emptyList<String>())
    val pin = flow(Prefs.pin(app), "")
    val external = flow(Prefs.external(app), false)

    private var currentPlaylist: Playlist? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playError = true }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) playError = false }
        })
        viewModelScope.launch {
            combine(Prefs.playlists(app), Prefs.activeIndex(app)) { l, i -> l.getOrNull(i) }
                .distinctUntilChanged()
                .collectLatest { pl -> currentPlaylist = pl; load(pl) }
        }
    }

    private suspend fun load(pl: Playlist?) {
        error = null
        if (pl == null) { channels = emptyList(); loading = false; return }
        loading = true
        try {
            val list = Repository.loadPlaylist(app, pl.url)
            channels = list
            if (list.isEmpty()) error = "Playlist is empty or the link is invalid"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Could not load the playlist"
        }
        loading = false
    }

    fun retry() { viewModelScope.launch { load(currentPlaylist) } }

    fun play(ch: Channel) {
        current = ch
        playError = false
        player.setMediaItem(MediaItem.fromUri(ch.url))
        player.prepare()
        player.playWhenReady = true
        viewModelScope.launch { Prefs.addRecent(app, ch.id) }
    }

    fun unlock(group: String) { unlocked = unlocked + group }

    fun toggleFavorite(id: String) = viewModelScope.launch { Prefs.toggleFavorite(app, id) }
    fun toggleHidden(g: String) = viewModelScope.launch { Prefs.toggleHidden(app, g) }
    fun toggleLocked(g: String) = viewModelScope.launch { Prefs.toggleLocked(app, g) }
    fun clearRecent() = viewModelScope.launch { Prefs.clearRecent(app) }
    fun setPin(p: String) = viewModelScope.launch { Prefs.setPin(app, p) }
    fun setExternal(v: Boolean) = viewModelScope.launch { Prefs.setExternal(app, v) }
    fun addPlaylist(name: String, url: String) = viewModelScope.launch { Prefs.addPlaylist(app, name, url) }
    fun setActive(i: Int) = viewModelScope.launch { Prefs.setActive(app, i) }
    fun removePlaylist(i: Int) = viewModelScope.launch { Prefs.removePlaylist(app, i) }

    override fun onCleared() {
        player.release()
    }
}
