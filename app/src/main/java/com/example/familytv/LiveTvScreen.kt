package com.example.familytv

import android.view.LayoutInflater
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Pal.blue) }
        vm.error != null -> StateCard(vm.error ?: "") {
            PillButton("Retry", onClick = { vm.retry() })
            if (canEdit) PillButton("Edit account", brush = null, onClick = { editing = true })
        }
        vm.channels.isEmpty() -> StateCard("No channels yet. Open Settings and add an Xtream account or a playlist link.") {}
        else -> LiveTvContent(vm, query, searchFocus, onFullscreen)
    }

    if (editing && activePlaylist != null) {
        if (activePlaylist.user.isNotBlank()) XtreamDialog(vm, active, activePlaylist) { editing = false }
        else AddPlaylistDialog(vm, active, activePlaylist) { editing = false }
    }
}

@Composable
private fun StateCard(message: String, buttons: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, color = Color.White, fontSize = 16.sp)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { buttons() }
        }
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

    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // التصنيفات
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cats, key = { it.key }) { c ->
                CategoryPill(c.label, c.count, selected = c.key == effectiveCat && query.isBlank()) {
                    if (c.key in locked && pin.isNotEmpty() && c.key !in vm.unlocked) pinFor = c.key
                    else selectedCat = c.key
                }
            }
        }

        // القنوات
        LazyColumn(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { ch ->
                val sel = vm.current?.id == ch.id
                ChannelRow(ch, selected = sel, isFav = ch.id in favs, onToggleFav = { vm.toggleFavorite(ch.id) }) {
                    if (external) openExternal(ctx, vm.urlFor(ch))
                    else if (sel) onFullscreen()
                    else vm.play(ch)
                }
            }
        }

        // المعاينة
        Column(Modifier.weight(2.2f)) {
            val previewShape = RoundedCornerShape(22.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(previewShape)
                    .background(Color.Black)
                    .border(1.dp, Pal.stroke, previewShape)
                    .clickable { if (vm.current != null) onFullscreen() }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        (LayoutInflater.from(c).inflate(R.layout.preview_player, null) as PlayerView)
                            .apply { player = vm.player }
                    },
                    update = { it.player = vm.player },
                    onRelease = { it.player = null }
                )
                if (vm.current == null) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Pal.textDim, modifier = Modifier.size(44.dp))
                        Text("Pick a channel to start", color = Pal.textDim)
                    }
                } else if (vm.playError) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Stream unavailable", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(vm.playErrorInfo, color = Pal.textDim, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp))
                }
                RecordButton(vm, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.current?.name ?: "",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(vm.current?.group ?: "", color = Pal.textDim, fontSize = 13.sp, maxLines = 1)
                }
                SignalMeter(vm)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cur = vm.current
                val isFav = cur != null && cur.id in favs
                PillButton("Fullscreen", icon = Icons.Default.PlayArrow, enabled = cur != null) { onFullscreen() }
                PillButton(
                    if (isFav) "Remove Favorite" else "Add to Favorite",
                    icon = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    brush = null, enabled = cur != null
                ) { cur?.let { vm.toggleFavorite(it.id) } }
                PillButton("Search", icon = Icons.Default.Search, brush = null) { searchFocus.requestFocus() }
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
private fun CategoryPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "cat")
    Row(
        Modifier
            .fillMaxWidth()
            .focusBorder(shape)
            .clip(shape)
            .background(Pal.glass)
            .background(Pal.accentBrush, shape, alpha = a)
            .bounceClick(onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color.White,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Text(
            "$count", color = if (selected) Color.White else Pal.textDim, fontSize = 12.sp,
            modifier = Modifier.clip(CircleShape).background(Color(0x22FFFFFF)).padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ChannelRow(ch: Channel, selected: Boolean, isFav: Boolean, onToggleFav: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "row")
    val sel = Brush.horizontalGradient(listOf(Pal.violet.copy(alpha = 0.5f), Pal.blue.copy(alpha = 0.28f)))
    Row(
        Modifier
            .fillMaxWidth()
            .focusBorder(shape)
            .clip(shape)
            .background(Pal.glass)
            .background(sel, shape, alpha = a)
            .bounceClick(onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${ch.num}", color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.width(36.dp))
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (ch.logo.isNotBlank()) {
                AsyncImage(
                    model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Pal.textDim)
            }
        }
        Text(
            ch.name, color = Color.White,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        )
        Box(
            Modifier.size(36.dp).focusBorder(CircleShape).clip(CircleShape).bounceClick(onToggleFav),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                tint = if (isFav) Pal.pink else Pal.textDim,
                modifier = Modifier.size(20.dp)
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            NowPlayingBars()
        }
    }
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
