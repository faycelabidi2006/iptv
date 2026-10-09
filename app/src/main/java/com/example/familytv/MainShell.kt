package com.example.familytv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainShell(vm: MainViewModel, onSettings: () -> Unit, onFullscreen: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.player.pause() }

    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                tab = tab, onTab = { tab = it },
                query = query,
                onQuery = { query = it; if (it.isNotBlank()) tab = 1 },
                searchFocus = searchFocus,
                onSettings = onSettings
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeTab(vm, onTab = { tab = it }, onSettings = onSettings)
                    1 -> LiveTvScreen(vm, query, searchFocus, onFullscreen)
                    else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Movies & Series need a provider with an on-demand API (Xtream).\nComing in the next step.",
                            color = Color.White, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    tab: Int, onTab: (Int) -> Unit,
    query: String, onQuery: (String) -> Unit,
    searchFocus: FocusRequester, onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Home", "Live TV", "Movies", "Series").forEachIndexed { i, t ->
            Text(
                t,
                color = if (tab == i) Pal.accent else Color.White,
                fontSize = 22.sp,
                modifier = Modifier.focusBorder().clickable { onTab(i) }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
            if (i < 3) Text("|", color = Color.Gray, fontSize = 22.sp)
        }
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.width(280.dp).focusRequester(searchFocus)
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
    }
}

@Composable
private fun HomeTab(vm: MainViewModel, onTab: (Int) -> Unit, onSettings: () -> Unit) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val byId = remember(vm.channels) { vm.channels.associateBy { it.id } }
    val recents = recent.mapNotNull { byId[it] }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTile("Live TV") { onTab(1) }
            HomeTile("Movies") { onTab(2) }
            HomeTile("Series") { onTab(3) }
            HomeTile("Settings") { onSettings() }
        }
        if (vm.playlists.collectAsStateWithLifecycle().value.isEmpty()) {
            Text("No playlist yet. Open Settings and choose Add Playlist.", color = Color.White)
        }
        if (recents.isNotEmpty()) {
            Text("Recently viewed", color = Color.White, style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recents, key = { it.num }) { ch ->
                    Card(
                        onClick = { vm.play(ch); onTab(1) },
                        modifier = Modifier.focusBorder()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(ch.name, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTile(label: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(170.dp).focusBorder()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), Alignment.Center) {
            Text(label, fontSize = 20.sp)
        }
    }
}
