package com.example.familytv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 30 }).togetherWith(fadeOut(tween(120)))
                },
                label = "tab"
            ) { t ->
                when (t) {
                    0 -> HomeTab(vm, onTab = { tab = it }, onSettings = onSettings)
                    1 -> LiveTvScreen(vm, query, searchFocus, onFullscreen)
                    2 -> ComingSoon("Movies")
                    else -> ComingSoon("Series")
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    tab: Int, onTab: (Int) -> Unit,
    query: String, onQuery: (String) -> Unit,
    searchFocus: FocusRequester, onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.accentBrush),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) }
        Spacer(Modifier.width(14.dp))

        Row(Modifier.glass(CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Home", "Live TV", "Movies", "Series").forEachIndexed { i, t ->
                TabPill(t, selected = tab == i) { onTab(i) }
            }
        }
        Spacer(Modifier.width(14.dp))
        SearchPill(query, onQuery, searchFocus)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onSettings),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White) }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "tabBg")
    val textColor by animateColorAsState(if (selected) Color.White else Pal.textDim, tween(250), label = "tabText")
    Box(
        Modifier
            .focusBorder(CircleShape)
            .clip(CircleShape)
            .background(Pal.accentBrush, CircleShape, alpha = bgAlpha)
            .bounceClick(onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, focus: FocusRequester) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Pal.blue),
        modifier = Modifier
            .width(250.dp)
            .height(42.dp)
            .focusRequester(focus)
            .focusBorder(CircleShape),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().glass(CircleShape).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Pal.textDim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search channels", color = Pal.textDim, fontSize = 15.sp)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun HomeTab(vm: MainViewModel, onTab: (Int) -> Unit, onSettings: () -> Unit) {
    val recent by vm.recent.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    val byId = remember(vm.channels) { vm.channels.associateBy { it.id } }
    val recents = recent.mapNotNull { byId[it] }
    val subtitle = if (playlists.isEmpty()) "Add an account to get started"
    else "${playlists.getOrNull(active)?.name ?: ""}  -  ${vm.channels.size} channels"

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Entrance(0) {
            Column {
                Text("What do you want to watch?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Pal.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Entrance(1) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroTile("Live TV", "Channels now", Icons.Default.PlayArrow, Pal.accentBrush, Modifier.weight(1f)) { onTab(1) }
                HeroTile("Movies", "On demand", Icons.Default.Star, Pal.warmBrush, Modifier.weight(1f)) { onTab(2) }
                HeroTile("Series", "On demand", Icons.Default.List, Pal.coolBrush, Modifier.weight(1f)) { onTab(3) }
                HeroTile("Settings", "Accounts and more", Icons.Default.Settings, Pal.slateBrush, Modifier.weight(1f)) { onSettings() }
            }
        }
        if (playlists.isEmpty()) {
            Entrance(2) {
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "No channels yet. Add your Xtream account or a playlist link.",
                        color = Color.White, modifier = Modifier.weight(1f)
                    )
                    PillButton("Open settings", onClick = onSettings)
                }
            }
        } else if (recents.isNotEmpty()) {
            Entrance(2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Continue watching", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recents, key = { it.id }) { ch ->
                            Row(
                                Modifier
                                    .width(230.dp)
                                    .focusBorder(RoundedCornerShape(16.dp))
                                    .glass(RoundedCornerShape(16.dp))
                                    .bounceClick { vm.play(ch); onTab(1) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape).background(Pal.accentBrush),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                                Text(
                                    ch.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroTile(
    title: String, subtitle: String, icon: ImageVector, brush: Brush,
    modifier: Modifier, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .height(124.dp)
            .focusBorder(shape)
            .clip(shape)
            .background(brush)
            .bounceClick(onClick)
            .padding(16.dp)
    ) {
        Icon(
            icon, contentDescription = null, tint = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(96.dp).align(Alignment.BottomEnd).offset(x = 22.dp, y = 22.dp)
        )
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ComingSoon(name: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            Modifier.glass(RoundedCornerShape(24.dp)).padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Needs an account with on-demand content. Coming in the next update.",
                color = Pal.textDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
