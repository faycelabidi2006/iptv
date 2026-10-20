package com.example.familytv

import android.app.Application
import android.net.TrafficStats
import android.net.Uri
import android.os.Process
import android.os.SystemClock
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
import com.example.familytv.data.AppUser
import com.example.familytv.data.Auth
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
import kotlinx.coroutines.flow.first
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

    // قياس جودة التدفق
    private val stallTimes = ArrayList<Long>()
    private var wasReady = false
    private var bufferingSince = 0L
    private var lastRx = -1L
    private var lastRxTime = 0L
    private var smoothMbps: Float? = null

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
    val recordDir = flow(Prefs.recordDir(app), "")
    val trustAll = flow(Prefs.trustAll(app), false)
    val tmdbKey = flow(Prefs.tmdbKey(app), "")
    fun setTmdbKey(v: String) { viewModelScope.launch { Prefs.setTmdbKey(app, v) } }

    fun toggleTrustAll() {
        viewModelScope.launch {
            val v = !trustAll.value
            Prefs.setTrustAll(app, v)
            com.example.familytv.data.Net.setTrustAll(v)
            load(currentPlaylist)
        }
    }

    val users = flow(Prefs.users(app), emptyList<AppUser>())

    var authReady by mutableStateOf(false); private set
    var sessionUser by mutableStateOf<String?>(null); private set
    private var failures = 0
    private var lockedUntil = 0L

    init {
        viewModelScope.launch {
            Prefs.ensureOwner(app)
            val saved = Prefs.session(app).first()
            sessionUser = Prefs.users(app).first().firstOrNull { it.name == saved }?.name
            authReady = true
        }
    }

    /** onResult(null) = نجاح، وإلا رسالة الخطأ. */
    fun login(name: String, pass: String, stay: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) {
                onResult("Too many attempts. Try again in ${(lockedUntil - now) / 1000 + 1}s")
                return@launch
            }
            val list = Prefs.users(app).first()
            val u = list.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            val ok = u != null && withContext(Dispatchers.Default) { Auth.verify(pass, u) }
            if (ok && u != null) {
                failures = 0
                sessionUser = u.name
                Prefs.setSession(app, if (stay) u.name else "")
                onResult(null)
            } else {
                failures++
                if (failures >= 5) { lockedUntil = now + 30_000; failures = 0 }
                onResult("Wrong username or password")
            }
        }
    }

    fun logout() {
        player.stop()
        player.clearMediaItems()
        current = null
        sessionUser = null
        viewModelScope.launch { Prefs.setSession(app, "") }
    }

    fun changePassword(old: String, new: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            if (me == null) { onResult("Not signed in"); return@launch }
            if (new.length < 4) { onResult("New password is too short (min 4)"); return@launch }
            if (!withContext(Dispatchers.Default) { Auth.verify(old, me) }) {
                onResult("Current password is wrong"); return@launch
            }
            val salt = Auth.newSalt()
            val h = withContext(Dispatchers.Default) { Auth.hash(new, salt) }
            Prefs.saveUsers(app, list.map { if (it.name == me.name) it.copy(salt = salt, hash = h) else it })
            onResult("Password changed")
        }
    }

    fun addUser(name: String, pass: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            val n = name.trim()
            when {
                me == null || !me.owner -> onResult("Only the owner can add users")
                n.length < 3 -> onResult("Username is too short (min 3)")
                pass.length < 4 -> onResult("Password is too short (min 4)")
                list.any { it.name.equals(n, ignoreCase = true) } -> onResult("This username already exists")
                else -> {
                    val salt = Auth.newSalt()
                    val h = withContext(Dispatchers.Default) { Auth.hash(pass, salt) }
                    Prefs.saveUsers(app, list + AppUser(n, salt, h, owner = false))
                    onResult("User added")
                }
            }
        }
    }

    fun removeUser(name: String) {
        viewModelScope.launch {
            val list = Prefs.users(app).first()
            val me = list.firstOrNull { it.name == sessionUser }
            if (me != null && me.owner && name != me.name) {
                Prefs.saveUsers(app, list.filter { it.name != name || it.owner })
            }
        }
    }

    private var currentPlaylist: Playlist? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playError = true
                playErrorInfo = "${error.errorCodeName}  ${error.cause?.message ?: ""}".trim()
            }
            override fun onPlaybackStateChanged(state: Int) {
                val now = System.currentTimeMillis()
                when (state) {
                    Player.STATE_READY -> { playError = false; wasReady = true; bufferingSince = 0L }
                    Player.STATE_BUFFERING -> {
                        if (bufferingSince == 0L) bufferingSince = now
                        if (wasReady) stallTimes.add(now)
                    }
                    else -> wasReady = false
                }
            }
        })
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
                refreshQuietly()
            }
        }
        viewModelScope.launch {
            combine(Prefs.playlists(app), Prefs.activeIndex(app)) { l, i -> l.getOrNull(i) }
                .distinctUntilChanged()
                .collectLatest { pl -> currentPlaylist = pl; load(pl) }
        }
    }

    /** تحديث صامت: لا يمسح القنوات الحالية ولا يعرض شاشة تحميل، ويبقى القديم إن فشل. */
    private suspend fun refreshQuietly() {
        val pl = currentPlaylist ?: return
        if (pl.user.isNotBlank() || pl.url.startsWith("file://")) return
        try {
            val list = Repository.loadPlaylist(app, pl.url)
            if (list.isNotEmpty() && currentPlaylist == pl) channels = list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { /* نبقي القائمة الحالية */ }
    }

    private suspend fun load(pl: Playlist?) {
        com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
        error = null
        if (pl == null) { channels = emptyList(); loading = false; return }
        loading = true
        try {
            val list = when {
                pl.user.isNotBlank() -> Repository.loadXtream(pl.url, pl.user, pl.pass)
                com.example.familytv.data.IptvOrg.isPack(pl.url) ->
                    com.example.familytv.data.IptvOrg.load(app) { partial ->
                        if (currentPlaylist == pl) { channels = partial; loading = false }
                    }
                else -> Repository.loadPlaylist(app, pl.url)
            }
            channels = list
            if (list.isEmpty()) error = "No channels found. Check the account or the link."
        } catch (e: CancellationException) {
            throw e
        } catch (e: XtreamException) {
            channels = emptyList()
            error = e.message
        } catch (e: Exception) {
            channels = emptyList()
            error = "Could not load: ${e.message ?: e.javaClass.simpleName}"
        }
        loading = false
    }

    // أفلام Xtream (VOD)
    var movies by mutableStateOf<List<Channel>>(emptyList()); private set
    var moviesLoading by mutableStateOf(false); private set
    var moviesError by mutableStateOf<String?>(null); private set
    private var moviesFor: Playlist? = null

    fun loadMovies(force: Boolean = false) {
        val pl = currentPlaylist
        if (pl == null || pl.user.isBlank()) {
            movies = emptyList(); moviesError = null; moviesFor = null
            return
        }
        if (!force && moviesFor == pl && movies.isNotEmpty()) return
        if (moviesLoading && !force) return
        viewModelScope.launch {
            moviesLoading = true
            moviesError = null
            try {
                com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
                val list = Repository.loadXtreamMovies(pl.url, pl.user, pl.pass)
                if (currentPlaylist == pl) {
                    movies = list
                    moviesFor = pl
                    if (list.isEmpty()) moviesError = "No movies found on this account."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                movies = emptyList(); moviesError = e.message
            } catch (e: Exception) {
                movies = emptyList(); moviesError = "Could not load movies: ${e.message ?: e.javaClass.simpleName}"
            }
            moviesLoading = false
        }
    }

    // مسلسلات Xtream
    var series by mutableStateOf<List<Channel>>(emptyList()); private set
    var seriesLoading by mutableStateOf(false); private set
    var seriesError by mutableStateOf<String?>(null); private set
    private var seriesFor: Playlist? = null

    var openSeries by mutableStateOf<Channel?>(null); private set
    var episodes by mutableStateOf<List<com.example.familytv.data.Episode>>(emptyList()); private set
    var episodesLoading by mutableStateOf(false); private set
    var episodesError by mutableStateOf<String?>(null); private set

    fun loadSeries(force: Boolean = false) {
        val pl = currentPlaylist
        if (seriesFor != pl) { series = emptyList(); openSeries = null; seriesFor = null }
        if (pl == null || pl.user.isBlank()) { seriesError = null; return }
        if (!force && seriesFor == pl && series.isNotEmpty()) return
        if (seriesLoading && !force) return
        viewModelScope.launch {
            seriesLoading = true
            seriesError = null
            try {
                com.example.familytv.data.Net.setTrustAll(Prefs.trustAll(app).first())
                val list = Repository.loadXtreamSeries(pl.url, pl.user, pl.pass)
                if (currentPlaylist == pl) {
                    series = list
                    seriesFor = pl
                    if (list.isEmpty()) seriesError = "No series found on this account."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                series = emptyList(); seriesError = e.message
            } catch (e: Exception) {
                series = emptyList(); seriesError = "Could not load series: ${e.message ?: e.javaClass.simpleName}"
            }
            seriesLoading = false
        }
    }

    fun openSeries(ch: Channel) {
        openSeries = ch
        loadEpisodes(ch)
    }

    fun closeSeries() { openSeries = null; episodes = emptyList(); episodesError = null }

    fun loadEpisodes(ch: Channel) {
        val pl = currentPlaylist ?: return
        viewModelScope.launch {
            episodesLoading = true
            episodesError = null
            episodes = emptyList()
            try {
                val list = Repository.loadXtreamEpisodes(pl.url, pl.user, pl.pass, ch.url)
                if (openSeries?.id == ch.id) {
                    episodes = list
                    if (list.isEmpty()) episodesError = "No episodes found for this series."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: XtreamException) {
                episodesError = e.message
            } catch (e: Exception) {
                episodesError = "Could not load episodes: ${e.message ?: e.javaClass.simpleName}"
            }
            episodesLoading = false
        }
    }

    /** يشغّل حلقة: نبني Channel مؤقتاً كي يعمل المشغّل نفسه. */
    fun playEpisode(show: Channel, ep: com.example.familytv.data.Episode) {
        play(
            Channel(
                id = "e${ep.id}", num = ep.num,
                name = "${show.name}  S${ep.season}E${ep.num}",
                logo = show.logo, url = ep.url, group = show.group, xtream = false
            ),
            addToRecent = false
        )
    }

    fun retry() { viewModelScope.launch { load(currentPlaylist) } }

    /** تحديث يدوي فوري. */
    fun refreshNow() { viewModelScope.launch { load(currentPlaylist) } }

    /** رابط التشغيل النهائي (لقنوات Xtream يُضاف الامتداد حسب الصيغة المختارة). */
    fun urlFor(ch: Channel): String = if (ch.xtream) "${ch.url}.${format.value}" else ch.url

    fun play(ch: Channel, addToRecent: Boolean = true) {
        current = ch
        playError = false
        stallTimes.clear()
        wasReady = false
        bufferingSince = System.currentTimeMillis()
        player.setMediaItem(MediaItem.fromUri(urlFor(ch)))
        player.prepare()
        player.playWhenReady = true
        if (addToRecent) viewModelScope.launch { Prefs.addRecent(app, ch.id) }
    }

    /** سرعة التدفق التقريبية (ميغابت/ثانية) من حركة بيانات التطبيق نفسه. */
    private fun measureMbps(): Float? {
        val rx = TrafficStats.getUidRxBytes(Process.myUid())
        if (rx == TrafficStats.UNSUPPORTED.toLong()) return null
        val now = SystemClock.elapsedRealtime()
        var result: Float? = null
        if (lastRx >= 0 && now > lastRxTime) {
            val seconds = (now - lastRxTime) / 1000f
            val inst = (rx - lastRx).toFloat() * 8f / seconds / 1_000_000f
            val prev = smoothMbps
            smoothMbps = if (prev == null) inst else prev * 0.6f + inst * 0.4f
            result = smoothMbps
        }
        lastRx = rx
        lastRxTime = now
        return result
    }

    /**
     * يقدّر قوة التدفق من حالة المشغّل: حجم ما حُمّل مسبقاً، وعدد مرات التقطّع
     * في آخر 30 ثانية، ووقت الانتظار الحالي.
     */
    fun computeSignal(): Signal {
        val mbps = measureMbps()
        if (current == null) return Signal(-1, "", null)
        if (playError) return Signal(0, "No signal", null)

        val now = System.currentTimeMillis()
        val state = player.playbackState
        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) {
            val since = if (bufferingSince > 0L) bufferingSince else now
            return if (now - since > 8_000) Signal(0, "No signal", mbps) else Signal(1, "Connecting", mbps)
        }

        val bufferSeconds = player.totalBufferedDuration / 1000f
        var level = when {
            bufferSeconds >= 8f -> 4
            bufferSeconds >= 5f -> 3
            bufferSeconds >= 2.5f -> 2
            else -> 1
        }
        stallTimes.removeAll { now - it > 30_000 }
        val stalls = stallTimes.size
        if (stalls >= 3) level = minOf(level, 1) else if (stalls >= 1) level = minOf(level, 2)

        val label = when (level) {
            4 -> "Excellent"
            3 -> "Good"
            2 -> "Weak"
            else -> "Very weak"
        }
        return Signal(level, label, mbps)
    }

    fun setRecordDir(v: String) = viewModelScope.launch { Prefs.setRecordDir(app, v) }

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
