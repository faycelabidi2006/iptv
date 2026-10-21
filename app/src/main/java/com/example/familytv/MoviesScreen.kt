package com.example.familytv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.familytv.data.Channel

private const val MOVIES_ALL = "__all"
private const val MOVIES_FAV = "__fav"

/** تبويب الأفلام: يعمل مع حسابات Xtream فقط (قوائم M3U لا تفصل الأفلام عن البث المباشر). */
@Composable
fun MoviesScreen(vm: MainViewModel, onFullscreen: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val pl = playlists.getOrNull(active)
    val isXtream = pl != null && pl.user.isNotBlank()

    // عند العودة من ملء الشاشة أو الدخول للتبويب: نوقف البث الجاري كي لا يستمر صوته دون واجهة
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.player.pause() }
    LaunchedEffect(pl) { vm.loadMovies() }

    when {
        !isXtream -> MoviesMessage("Movies need an Xtream account. Open Settings and add one (a plain M3U link has no separate movie list).") {}
        vm.moviesLoading && vm.movies.isEmpty() ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Pal.blue) }
        vm.moviesError != null -> MoviesMessage(vm.moviesError ?: "") {
            PillButton("Retry", onClick = { vm.loadMovies(force = true) })
        }
        else -> PosterBrowser(vm.movies, "movies", favs, { vm.toggleFavorite(it) }) { m ->
            vm.play(m, addToRecent = false)
            onFullscreen()
        }
    }
}

@Composable
fun MoviesMessage(message: String, buttons: @Composable RowScope.() -> Unit) {
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

/** شبكة بوسترات مع بحث وتصنيفات، تُستعمل للأفلام والمسلسلات. */
@Composable
fun PosterBrowser(
    items: List<Channel>, noun: String,
    favs: Set<String>, onToggleFav: (String) -> Unit,
    onPick: (Channel) -> Unit
) {
    var cat by rememberSaveable(noun) { mutableStateOf(MOVIES_ALL) }
    var search by rememberSaveable(noun) { mutableStateOf("") }

    val cats = remember(items) { items.map { it.group }.distinct() }
    val favCount = remember(items, favs) { items.count { it.id in favs } }
    val shown = remember(items, cat, search, favs) {
        val q = search.trim()
        items.filter {
            val inCat = when (cat) {
                MOVIES_ALL -> true
                MOVIES_FAV -> it.id in favs
                else -> it.group == cat
            }
            inCat && (q.isEmpty() || it.name.contains(q, ignoreCase = true))
        }
    }

    val compact = isCompact()
    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                label = { Text("Search $noun") }, singleLine = true,
                modifier = if (compact) Modifier.weight(1f) else Modifier.width(260.dp)
            )
            Text("${shown.size} $noun", color = Pal.textDim, fontSize = 13.sp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PillButton("All", brush = if (cat == MOVIES_ALL) Pal.accentBrush else null, onClick = { cat = MOVIES_ALL })
            }
            item {
                PillButton(
                    "Favorites ($favCount)", icon = Icons.Default.Favorite,
                    brush = if (cat == MOVIES_FAV) Pal.accentBrush else null,
                    onClick = { cat = MOVIES_FAV }
                )
            }
            items(cats, key = { it }) { c ->
                PillButton(c, brush = if (cat == c) Pal.accentBrush else null, onClick = { cat = c })
            }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No matching $noun.", color = Pal.textDim) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compact) 105.dp else 130.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown, key = { it.id }) { m ->
                    PosterCard(m, isFav = m.id in favs, onToggleFav = { onToggleFav(m.id) }) { onPick(m) }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(m: Channel, isFav: Boolean, onToggleFav: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .focusBorder(shape)
            .glass(shape)
            .bounceClick(onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Pal.glassStrong)
        ) {
            if (m.logo.isNotBlank()) {
                AsyncImage(
                    model = m.logo,
                    contentDescription = m.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
                    .clip(CircleShape).background(Color(0x99000000)).bounceClick(onToggleFav),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                    tint = if (isFav) Pal.pink else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            m.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}
