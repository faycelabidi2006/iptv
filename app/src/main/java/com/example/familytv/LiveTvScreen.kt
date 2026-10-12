package com.example.familytv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.familytv.data.Channel

private const val CAT_RECENT = "__recent"
private const val CAT_ALL = "__all"
private const val CAT_FAV = "__fav"

private data class Cat(val key: String, val label: String, val count: Int)

@Composable
fun LiveTvScreen(vm: MainViewModel, query: String, searchFocus: FocusRequester, onFullscreen: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    val activePlaylist = playlists.getOrNull(active)
    val canEdit = activePlaylist != null && !activePlaylist.url.startsWith("file://")

    when {
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        vm.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(vm.error ?: "", color = Color.White)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                    if (canEdit) Button(onClick = { editing = true }) { Text("Edit account") }
                }
            }
        }
        vm.channels.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No playlist. Go to Settings > Add Xtream Account or Add Playlist.", color = Color.White)
        }
        else -> LiveTvContent(vm, query, searchFocus, onFullscreen)
    }

    if (editing && activePlaylist != null) {
        if (activePlaylist.user.isNotBlank()) XtreamDialog(vm, active, activePlaylist) { editing = false }
        else AddPlaylistDialog(vm, active, activePlaylist) { editing = false }
    }
}

@Composable
private fun LiveTvContent(vm: MainViewModel, query: String, searchFocus: FocusRequester, onFullscreen: () -> Unit) {
    val ctx = LocalContext.current
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val pin by vm.pin.collectAsStateWithLifecycle()
    val external by vm.external.collectAsStateWithLifecycle()

    var selectedCat by rememberSaveable { mutableStateOf(CAT_ALL) }
    var pinFor by remember { mutableStateOf<String?>(null) }

    val channels = vm.channels
    val groups = remember(channels) { channels.map { it.group }.distinct() }
    val counts = remember(channels) { channels.groupingBy { it.group }.eachCount() }
    val byId = remember(channels) { channels.associateBy { it.id } }
    val recentChannels = remember(recent, byId) { recent.mapNotNull { byId[it] } }

    val cats = remember(groups, hidden, counts, recentChannels, favs, channels) {
        buildList {
            add(Cat(CAT_RECENT, "Recently Viewed", recentChannels.size))
            add(Cat(CAT_ALL, "All", channels.count { it.group !in hidden }))
            add(Cat(CAT_FAV, "Favorite", channels.count { it.id in favs }))
            groups.filter { it !in hidden }.forEach { add(Cat(it, it, counts[it] ?: 0)) }
        }
    }
    val effectiveCat = if (cats.any { it.key == selectedCat }) selectedCat else CAT_ALL

    val shown = remember(channels, hidden, effectiveCat, query, favs, recentChannels) {
        when {
            query.isNotBlank() -> channels.filter { it.name.contains(query, ignoreCase = true) }
            effectiveCat == CAT_RECENT -> recentChannels
            effectiveCat == CAT_ALL -> channels.filter { it.group !in hidden }
            effectiveCat == CAT_FAV -> channels.filter { it.id in favs }
            else -> channels.filter { it.group == effectiveCat }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // العمود 1: التصنيفات
        LazyColumn(Modifier.weight(1f)) {
            items(cats, key = { it.key }) { c ->
                val sel = c.key == effectiveCat && query.isBlank()
                ListRow(onClick = {
                    if (c.key in locked && pin.isNotEmpty() && c.key !in vm.unlocked) pinFor = c.key
                    else selectedCat = c.key
                }) {
                    Text(
                        c.label, color = if (sel) Pal.accent else Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text("${c.count}", color = if (sel) Pal.accent else Color.White)
                }
            }
        }

        // العمود 2: القنوات
        LazyColumn(Modifier.weight(1.2f).padding(horizontal = 6.dp)) {
            items(shown, key = { it.id }) { ch ->
                val sel = vm.current?.id == ch.id
                ListRow(onClick = {
                    if (external) openExternal(ctx, vm.urlFor(ch))
                    else if (sel) onFullscreen()
                    else vm.play(ch)
                }) {
                    Text("${ch.num}", color = Color.LightGray, modifier = Modifier.width(48.dp))
                    if (ch.logo.isNotBlank()) {
                        AsyncImage(model = ch.logo, contentDescription = null, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        ch.name, color = if (sel) Pal.accent else Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp).weight(1f)
                    )
                }
            }
        }

        // العمود 3: المعاينة
        Column(Modifier.weight(2.2f)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
                    .clickable { if (vm.current != null) onFullscreen() }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> PlayerView(c).apply { useController = false; player = vm.player } },
                    update = { it.player = vm.player },
                    onRelease = { it.player = null }
                )
                if (vm.playError && vm.current != null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Stream unavailable", color = Color.White)
                        Text(vm.playErrorInfo, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(
                vm.current?.name ?: "",
                color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Box(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp, start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cur = vm.current
                PurpleButton("Fullscreen", enabled = cur != null) { onFullscreen() }
                PurpleButton(
                    if (cur != null && cur.id in favs) "Remove Favorite" else "Add to Favorite",
                    enabled = cur != null
                ) { cur?.let { vm.toggleFavorite(it.id) } }
                PurpleButton("Search") { searchFocus.requestFocus() }
            }
        }
    }

    pinFor?.let { g ->
        PinDialog(
            correct = pin,
            onOk = { vm.unlock(g); selectedCat = g; pinFor = null },
            onCancel = { pinFor = null }
        )
    }
}

@Composable
private fun PurpleButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Pal.button),
        modifier = Modifier.focusBorder()
    ) { Text(text) }
}

@Composable
fun PinDialog(correct: String, onOk: () -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Enter PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(8); wrong = false },
                    singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                if (wrong) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { if (input == correct) onOk() else wrong = true }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
