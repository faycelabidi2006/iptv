package com.example.familytv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView

@Composable
fun FullscreenScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val activity = ctx as? android.app.Activity
        val controller: WindowInsetsControllerCompat? = activity?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply { useController = true; keepScreenOn = true; player = vm.player }
            },
            update = { it.player = vm.player },
            onRelease = { it.player = null }
        )
        Text(
            vm.current?.name ?: "", color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
        if (vm.playError) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Stream unavailable", color = Color.White)
                Button(onClick = { vm.current?.let { vm.play(it) } }, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Back") }
            }
        }
    }
}
