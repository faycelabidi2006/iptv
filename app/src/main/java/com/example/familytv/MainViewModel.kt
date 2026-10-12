package com.example.familytv

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
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
import com.example.familytv.data.XtreamException
import com.example.familytv.data.XtreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var current by mutableStateOf<Channel?>(null); private set
    var playError by mutableStateOf(false); private set
    var playErrorInfo by mutableStateOf(""); private set
    var unlocked by mutableStateOf<Set<String>>(emptySet()); private set

    val player: ExoPlayer = ExoPlayer.Builder(app).build()

    private fun <T> flow(f: Flow<T>, init: T) = f.stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val playlists = flow(Prefs.playlists(app), emptyList<Playlist>())
    val activeIndex = flow(Prefs.activeIndex(app), 0)
    val favorites = flow(Prefs.favorites(app), emptySet<String>())
    val hidden = flow(Prefs.hidden(app), emptySet<String>())
    val locked = flow(Prefs.locked(app), emptySet<String>())
    val recent = flow(Prefs.recent(app), emptyList<String>())
    val pin = flow(Prefs.pin(app), "")
    val external = flow(Prefs.external(app), false)
    val format = flow(Prefs.format(app), "ts")

    private var currentPlaylist: Playlist? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playError = true
                playErrorInfo = "${error.errorCodeName}  ${error.cause?.message ?: ""}".trim()
            }
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
            val list = if (pl.user.isNotBlank()) Repository.loadXtream(pl.url, pl.user, pl.pass)
            else Repository.loadPlaylist(app, pl.url)
            channels = list
            if (list.isEmpty()) error = "No channels found. Check the account or the link."
        } catch (e: CancellationException) {
            throw e
        } catch (e: XtreamException) {
            channels = emptyList()
            error = e.message
        } catch (e: Exception) {
            channels = emptyList()
            error = "Could not load. Check the connection and the details."
        }
        loading = false
    }

    fun retry() { viewModelScope.launch { load(currentPlaylist) } }

    /** رابط التشغيل النهائي (لقنوات Xtream يُضاف الامتداد حسب الصيغة المختارة). */
    fun urlFor(ch: Channel): String = if (ch.xtream) "${ch.url}.${format.value}" else ch.url

    fun play(ch: Channel) {
        current = ch
        playError = false
        player.setMediaItem(MediaItem.fromUri(urlFor(ch)))
        player.prepare()
        player.playWhenReady = true
        viewModelScope.launch { Prefs.addRecent(app, ch.id) }
    }

    fun toggleFormat() {
        viewModelScope.launch {
            Prefs.setFormat(app, if (format.value == "ts") "m3u8" else "ts")
            current?.let { play(it) }
        }
    }

    fun addXtream(name: String, server: String, user: String, pass: String) = viewModelScope.launch {
        Prefs.addPlaylist(app, name.ifBlank { "Xtream" }, Repository.normalizeServer(server), user.trim(), pass.trim())
    }

    /** يستورد ملف قائمة (M3U) أو ملفاً نصياً فيه بيانات Xtream من تخزين الجهاز. */
    fun importFile(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { doImport(uri) }
            } catch (e: Exception) {
                "Could not read the file"
            }
            onDone(msg)
        }
    }

    private fun doImport(uri: Uri): String {
        val cr = app.contentResolver
        val displayName = cr.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "Imported"
        val name = displayName.substringBeforeLast('.').ifBlank { "Imported" }

        val dest = File(app.filesDir, "imported_${System.currentTimeMillis()}.m3u")
        cr.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            ?: return "Could not open the file"

        val head = dest.bufferedReader().use { r ->
            val buf = CharArray(4096)
            val n = r.read(buf)
            if (n > 0) String(buf, 0, n) else ""
        }
        if (head.contains("#EXTINF") || head.trimStart().startsWith("#EXTM3U")) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, "file://" + dest.absolutePath) }
            return "Playlist imported"
        }

        if (dest.length() > 64 * 1024) { dest.delete(); return "Unrecognized file" }
        val text = dest.readText()
        dest.delete()

        val c = XtreamParser.parse(text)
        if (c != null) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, Repository.normalizeServer(c.server), c.user, c.pass) }
            return "Xtream account imported"
        }
        val link = Regex("""https?://\S+""").findAll(text)
            .map { it.value.trim('"', ',', '}', ' ') }
            .firstOrNull { it.contains("get.php") || it.contains(".m3u") }
        if (link != null) {
            viewModelScope.launch { Prefs.addPlaylist(app, name, link) }
            return "Playlist imported"
        }
        return "Unrecognized file. Expected an M3U playlist, or a text file with server, username and password."
    }

    fun updatePlaylist(i: Int, pl: Playlist) = viewModelScope.launch { Prefs.updatePlaylist(app, i, pl) }

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
