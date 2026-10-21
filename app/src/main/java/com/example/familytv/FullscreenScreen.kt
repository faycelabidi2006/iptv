package com.example.familytv

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import java.util.Locale

@Composable
fun FullscreenScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val activity = ctx as? android.app.Activity
        vm.inFullscreen = true
        val controller: WindowInsetsControllerCompat? = activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        // الفيديو أفقي دائماً، ثم نعيد الاتجاه الحر عند الخروج
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            vm.inFullscreen = false
            vm.saveProgressNow()
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    val tracks = rememberTracks(vm.player)
    var showTracks by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    // عند انتهاء مؤقت النوم نغلق شاشة التشغيل (الشاشة تبقى مضاءة أثناء التشغيل، فلا معنى لبقائها مفتوحة)
    val sleepStart = remember { vm.sleepFired }
    LaunchedEffect(vm.sleepFired) { if (vm.sleepFired != sleepStart) onBack() }
    val sleepLeft by produceState(0L, vm.sleepEndsAt) {
        while (vm.sleepEndsAt > 0L) {
            value = ((vm.sleepEndsAt - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1L)
            kotlinx.coroutines.delay(15_000L)
        }
        value = 0L
    }

    // صورة داخل صورة: عند العودة للتطبيق نلغي العلامة، وعند إغلاق النافذة الصغيرة (يصل onStop دون onResume) نوقف التشغيل
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.pipEntered = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (vm.pipEntered) { vm.pipEntered = false; vm.player.pause() }
    }

    val hasChoices = options(tracks, C.TRACK_TYPE_AUDIO).size > 1 || options(tracks, C.TRACK_TYPE_TEXT).isNotEmpty()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply { useController = true; keepScreenOn = true; player = vm.player }
            },
            update = { it.player = vm.player; it.useController = !vm.pipActive },
            onRelease = { it.player = null }
        )

        if (!vm.pipActive) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopStart)
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
        )
        Row(Modifier.align(Alignment.TopStart).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(vm.current?.name ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            if (!vm.playError && vm.current != null) LiveBadge()
        }

        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChoices) PillButton("Audio / Subtitles", brush = null) { showTracks = true }
            PillButton(if (sleepLeft > 0L) "Sleep ${sleepLeft}m" else "Sleep", brush = null) { showSleep = true }
            RecordButton(vm)
            SignalMeter(vm)
        }

        if (vm.nextEpisode != null) {
            PillButton(
                "Next episode",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 96.dp)
            ) { vm.playNext() }
        }
        }

        if (vm.playError) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE612162A), RoundedCornerShape(22.dp))
                    .padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Stream unavailable", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(vm.playErrorInfo, color = Pal.textDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Retry") { vm.current?.let { vm.play(it) } }
                    PillButton("Back", brush = null, onClick = onBack)
                }
            }
        }
    }

    if (showTracks) TrackDialog(vm.player, tracks) { showTracks = false }
    if (showSleep) SleepDialog(vm) { showSleep = false }
}


// ---------- مؤقت النوم ----------

@Composable
private fun SleepDialog(vm: MainViewModel, onClose: () -> Unit) {
    val options = listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes", 90 to "90 minutes")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sleep timer") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    ChoiceRow(label, selected = minutes == 0 && vm.sleepEndsAt == 0L) {
                        vm.setSleepTimer(minutes)
                        onClose()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}


// ---------- اختيار الصوت والترجمة ----------

private data class TrackOpt(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean)

@Composable
private fun rememberTracks(player: Player): Tracks {
    var t by remember { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { t = tracks }
        }
        player.addListener(l)
        t = player.currentTracks
        onDispose { player.removeListener(l) }
    }
    return t
}

private fun trackLabel(f: Format, n: Int): String {
    val lang = f.language?.takeIf { it != "und" }?.let {
        Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()).ifBlank { it }
    }
    val base = f.label ?: lang ?: "Track $n"
    val ch = when {
        f.channelCount == 6 -> " (5.1)"
        f.channelCount == 8 -> " (7.1)"
        f.channelCount > 2 -> " (${f.channelCount}ch)"
        else -> ""
    }
    return base + ch
}

private fun options(t: Tracks, type: Int): List<TrackOpt> {
    var n = 0
    return t.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            n++
            TrackOpt(g, i, trackLabel(g.getTrackFormat(i), n), g.isTrackSelected(i))
        }
    }
}

@Composable
private fun TrackDialog(player: Player, tracks: Tracks, onClose: () -> Unit) {
    val audio = options(tracks, C.TRACK_TYPE_AUDIO)
    val text = options(tracks, C.TRACK_TYPE_TEXT)
    var subsOff by remember { mutableStateOf(text.none { it.selected }) }

    fun pick(o: TrackOpt) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(o.group.type, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            .build()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Audio / Subtitles") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (audio.size > 1) {
                    Text("Audio", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                    audio.forEach { o -> ChoiceRow(o.label, o.selected) { pick(o) } }
                }
                if (text.isNotEmpty()) {
                    Text("Subtitles", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    ChoiceRow("Off", subsOff) {
                        subsOff = true
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    }
                    text.forEach { o -> ChoiceRow(o.label, o.selected && !subsOff) { subsOff = false; pick(o) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().focusBorder().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
