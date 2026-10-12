package com.example.familytv

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainShell(vm, onSettings = { nav.navigate("settings") }, onFullscreen = { nav.navigate("fullscreen") })
        }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
        composable("fullscreen") { FullscreenScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
