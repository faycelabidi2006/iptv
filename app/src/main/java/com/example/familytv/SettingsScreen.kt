package com.example.familytv

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val external by vm.external.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val toast = { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }

    AppBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).focusBorder()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Settings", color = Color.White, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
            }
            val tiles = listOf(
                Triple("xtream", "Add Xtream Account", Icons.Default.Add),
                Triple("add", "Add Playlist (M3U)", Icons.Default.Add),
                Triple("parent", "Parent control", Icons.Default.Lock),
                Triple("change", "Change playlist", Icons.Default.List),
                Triple("hide", "Hide Live Categories", Icons.Default.Close),
                Triple("clear", "Clear History", Icons.Default.Delete),
                Triple("external", "External player: " + if (external) "On" else "Off", Icons.Default.PlayArrow),
                Triple("format", "Stream format: " + format.uppercase(), Icons.Default.PlayArrow)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tiles, key = { it.first }) { (key, label, icon) ->
                    SettingTile(label, icon) {
                        when (key) {
                            "clear" -> { vm.clearRecent(); toast("History cleared") }
                            "external" -> {
                                vm.setExternal(!external)
                                toast(if (external) "External player: Off" else "External player: On (channels open in another app)")
                            }
                            "format" -> {
                                vm.toggleFormat()
                                toast("Stream format: " + (if (format == "ts") "M3U8" else "TS"))
                            }
                            "hide" -> { if (vm.channels.isEmpty()) toast("Load a playlist first") else dialog = key }
                            else -> dialog = key
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "add" -> AddPlaylistDialog(vm, onAdd = { n, u -> vm.addPlaylist(n, u); dialog = null }, onClose = { dialog = null })
        "xtream" -> XtreamDialog(vm, onClose = { dialog = null })
        "change" -> ChangePlaylistDialog(vm, onClose = { dialog = null })
        "hide" -> {
            val hidden by vm.hidden.collectAsStateWithLifecycle()
            GroupPickerDialog("Hide Live Categories", vm.channels.map { it.group }.distinct(), hidden,
                onToggle = { vm.toggleHidden(it) }, onClose = { dialog = null })
        }
        "parent" -> ParentDialog(vm, onClose = { dialog = null })
    }
}

@Composable
private fun SettingTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Pal.tile),
        modifier = Modifier.focusBorder()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Text(label, color = Color.White, modifier = Modifier.padding(start = 12.dp), maxLines = 2)
        }
    }
}

@Composable
private fun rememberImportPicker(vm: MainViewModel, onClose: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importFile(uri) { msg ->
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                if (msg.endsWith("imported")) onClose()
            }
        }
    }
    return { picker.launch(arrayOf("*/*")) }
}

// نافذة عريضة بدل العرض الافتراضي الضيق، لتناسب الوضع الأفقي على الهاتف
private val wideDialog = DialogProperties(usePlatformDefaultWidth = false)

@Composable
private fun AddPlaylistDialog(vm: MainViewModel, onAdd: (String, String) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val pickFile = rememberImportPicker(vm, onClose)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text("Add Playlist (M3U)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = pickFile, modifier = Modifier.fillMaxWidth().focusBorder()) {
                    Text("Import from file (M3U or Xtream details)")
                }
                Text("or enter a link manually:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(url, { url = it }, label = { Text("M3U link") }, singleLine = true, modifier = Modifier.weight(2f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onAdd(name.ifBlank { "Playlist" }, url.trim()) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun XtreamDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val pickFile = rememberImportPicker(vm, onClose)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text("Add Xtream Account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = pickFile, modifier = Modifier.fillMaxWidth().focusBorder()) {
                    Text("Import details from file")
                }
                // عمودان جنباً إلى جنب ليتسع المحتوى في الشاشة الأفقية
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name, { name = it }, label = { Text("Name (optional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            server, { server = it }, label = { Text("Server (http://host:port)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            user, { user = it }, label = { Text("Username") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    vm.addXtream(name, server, user, pass)
                    onClose()
                }
            }) { Text("Login") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun ChangePlaylistDialog(vm: MainViewModel, onClose: () -> Unit) {
    val list by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Change playlist") },
        text = {
            if (list.isEmpty()) Text("No playlists yet.")
            else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(list.withIndex().toList(), key = { it.index }) { (i, p) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == active, onClick = { vm.setActive(i) })
                        Text(p.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.removePlaylist(i) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun GroupPickerDialog(
    title: String, groups: List<String>, selected: Set<String>,
    onToggle: (String) -> Unit, onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            if (groups.isEmpty()) Text("Load a playlist first.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(groups, key = { it }) { g ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = g in selected, onCheckedChange = { onToggle(g) })
                        Text(g, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}

@Composable
private fun ParentDialog(vm: MainViewModel, onClose: () -> Unit) {
    val pin by vm.pin.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    var newPin by remember { mutableStateOf("") }
    val groups = vm.channels.map { it.group }.distinct()

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Parent control") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                    label = { Text(if (pin.isEmpty()) "Set PIN" else "New PIN (current is set)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Text("Locked categories (PIN required to open):", modifier = Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(groups, key = { it }) { g ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = g in locked, onCheckedChange = { vm.toggleLocked(g) })
                            Text(g, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newPin.isNotEmpty()) vm.setPin(newPin); onClose() }) { Text("Save") }
        },
        dismissButton = {
            if (pin.isNotEmpty()) TextButton(onClick = { vm.setPin(""); onClose() }) { Text("Remove PIN") }
        }
    )
}
