package com.example.familytv

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.VisualTransformation
import com.example.familytv.data.Playlist
import com.example.familytv.data.Repository
import com.example.familytv.data.XtreamException
import com.example.familytv.data.XtreamParser
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onPlay: () -> Unit = {}) {
    val external by vm.external.collectAsStateWithLifecycle()
    val format by vm.format.collectAsStateWithLifecycle()
    val trustAll by vm.trustAll.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val toast = { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    val recordDir by vm.recordDir.collectAsStateWithLifecycle()
    val saveLabel = remember(recordDir) { RecStorage.describe(ctx, recordDir) }

    AppBackground {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.align(Alignment.CenterStart).size(44.dp).focusBorder(CircleShape).glass(CircleShape).bounceClick(onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
                Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            }
            val tiles = listOf(
                Triple("xtream", "Add Xtream Account", Icons.Default.Add),
                Triple("add", "Add Playlist (M3U)", Icons.Default.Add),
                Triple("public", "Free public channels", Icons.Default.List),
                Triple("parent", "Parent control", Icons.Default.Lock),
                Triple("change", "Change playlist", Icons.Default.List),
                Triple("hide", "Hide Live Categories", Icons.Default.Close),
                Triple("refresh", "Refresh channels now", Icons.Default.PlayArrow),
                Triple("clear", "Clear History", Icons.Default.Delete),
                Triple("recordings", "Recordings", Icons.Default.DateRange),
                Triple("storage", "Save to: $saveLabel", Icons.Default.Place),
                Triple("external", "External player: " + if (external) "On" else "Off", Icons.Default.PlayArrow),
                Triple("format", "Stream format: " + format.uppercase(), Icons.Default.PlayArrow),
                Triple("tmdb", "Movie search key (TMDB)", Icons.Default.Search),
                Triple("tls", "Untrusted HTTPS: " + if (trustAll) "On" else "Off", Icons.Default.Lock),
                Triple("vpn", "Open VPN app", Icons.Default.Lock)
            )
            val users by vm.users.collectAsStateWithLifecycle()
            val isOwner = users.firstOrNull { it.name == vm.sessionUser }?.owner == true
            val allTiles = tiles + listOfNotNull(
                Triple("account", "Account: ${vm.sessionUser ?: ""}", Icons.Default.AccountCircle),
                if (isOwner) Triple("users", "Manage Users", Icons.Default.Person) else null,
                if (isOwner) Triple("control", "Control panel", Icons.Default.Settings) else null
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (isCompact()) 2 else 4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allTiles, key = { it.first }) { (key, label, icon) ->
                    SettingTile(label, icon, tileBrush(key)) {
                        when (key) {
                            "clear" -> { vm.clearRecent(); toast("History cleared") }
                            "tls" -> {
                                vm.toggleTrustAll()
                                toast(if (trustAll) "Untrusted HTTPS: Off" else "Untrusted HTTPS: On (less secure). Reloading...")
                            }
                            "refresh" -> { vm.refreshNow(); toast("Refreshing channels...") }
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
        "add" -> AddPlaylistDialog(vm, onClose = { dialog = null })
        "xtream" -> XtreamDialog(vm, onClose = { dialog = null })
        "public" -> PublicListsDialog(vm, onClose = { dialog = null })
        "storage" -> StorageDialog(vm, onClose = { dialog = null })
        "recordings" -> RecordingsDialog(vm, onPlay = onPlay, onClose = { dialog = null })
        "account" -> AccountDialog(vm, onClose = { dialog = null })
        "users" -> UsersDialog(vm, onClose = { dialog = null })
        "control" -> ControlPanelDialog(vm, onClose = { dialog = null })
        "change" -> ChangePlaylistDialog(vm, onClose = { dialog = null })
        "hide" -> {
            val hidden by vm.hidden.collectAsStateWithLifecycle()
            GroupPickerDialog("Hide Live Categories", vm.channels.map { it.group }.distinct(), hidden,
                onToggle = { vm.toggleHidden(it) }, onClose = { dialog = null })
        }
        "parent" -> ParentDialog(vm, onClose = { dialog = null })
        "tmdb" -> TmdbKeyDialog(vm, onClose = { dialog = null })
        "vpn" -> VpnDialog(onClose = { dialog = null })
    }
}

private fun tileBrush(key: String): Brush = when (key) {
    "xtream", "add" -> Pal.accentBrush
    "parent" -> Pal.warmBrush
    "change" -> Pal.coolBrush
    "public" -> Pal.coolBrush
    "hide", "format" -> Pal.slateBrush
    "clear" -> Pal.warmBrush
    "recordings" -> Pal.warmBrush
    "storage" -> Pal.coolBrush
    "account" -> Pal.coolBrush
    "users", "control" -> Pal.warmBrush
    else -> Pal.accentBrush
}

@Composable
private fun SettingTile(label: String, icon: ImageVector, brush: Brush, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().focusBorder(shape).glass(shape).bounceClick(onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Text(label, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp), maxLines = 2)
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
fun AddPlaylistDialog(vm: MainViewModel, editIndex: Int? = null, initial: Playlist? = null, onClose: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var scheme by remember { mutableStateOf(if (initial?.url?.startsWith("https://") == true) "https://" else "http://") }
    var rest by remember { mutableStateOf((initial?.url ?: "").removePrefix("http://").removePrefix("https://")) }
    val url = if (rest.isBlank()) "" else scheme + rest.trim()
    val applyLink: (String) -> Unit = { t ->
        val x = t.trim()
        when {
            x.startsWith("https://", ignoreCase = true) -> { scheme = "https://"; rest = x.substring(8) }
            x.startsWith("http://", ignoreCase = true) -> { scheme = "http://"; rest = x.substring(7) }
            else -> rest = x
        }
    }
    val pickFile = rememberImportPicker(vm, onClose)
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text(if (editIndex != null) "Edit Playlist (M3U)" else "Add Playlist (M3U)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editIndex == null) {
                    Button(onClick = pickFile, modifier = Modifier.fillMaxWidth().focusBorder()) {
                        Text("Import from file (M3U or Xtream details)")
                    }
                    Text("or enter a link manually:")
                }
                AdaptiveFields(8.dp) { w ->
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = w(1f))
                    OutlinedTextField(rest, { applyLink(it) }, label = { Text("M3U link") }, prefix = { Text(scheme) }, singleLine = true, modifier = w(2f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) {
                    val pl = Playlist(name.ifBlank { "Playlist" }, url.trim())
                    if (editIndex != null) vm.updatePlaylist(editIndex, pl) else vm.addPlaylist(pl.name, pl.url)
                    onClose()
                }
            }) { Text(if (editIndex != null) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
fun XtreamDialog(vm: MainViewModel, editIndex: Int? = null, initial: Playlist? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(initial?.name ?: "") }
    // البادئة http:// ثابتة أمام الخانة، ويكتب المستخدم العنوان والمنفذ فقط.
    // لو لصق عنواناً كاملاً يبدأ بـ https:// تتحول البادئة تلقائياً.
    var scheme by remember { mutableStateOf(if (initial?.url?.startsWith("https://") == true) "https://" else "http://") }
    var host by remember { mutableStateOf((initial?.url ?: "").removePrefix("http://").removePrefix("https://")) }
    val server = if (host.isBlank()) "" else scheme + host.trim()
    val applyServer: (String) -> Unit = { t ->
        val x = t.trim()
        when {
            x.startsWith("https://", ignoreCase = true) -> { scheme = "https://"; host = x.substring(8) }
            x.startsWith("http://", ignoreCase = true) -> { scheme = "http://"; host = x.substring(7) }
            else -> host = x
        }
    }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var pass by remember { mutableStateOf(initial?.pass ?: "") }
    var showPass by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val pickFile = rememberImportPicker(vm, onClose)

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text(if (editIndex != null) "Edit Xtream Account" else "Add Xtream Account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editIndex == null) {
                        Button(onClick = pickFile, modifier = Modifier.weight(1f).focusBorder()) { Text("Import from file") }
                    } else {
                        Button(
                            onClick = {
                                val text = "server: ${Repository.normalizeServer(server)}\nusername: ${user.trim()}\npassword: ${pass.trim()}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Share account"))
                            },
                            modifier = Modifier.weight(1f).focusBorder()
                        ) { Text("Share details") }
                    }
                    Button(
                        onClick = {
                            val c = XtreamParser.parse(clipboard.getText()?.text ?: "")
                            if (c != null) {
                                applyServer(c.server); user = c.user; pass = c.pass; result = ""
                                Toast.makeText(ctx, "Filled from clipboard. Review, then press Test.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(ctx, "Clipboard has no Xtream details", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).focusBorder()
                    ) { Text("Paste from clipboard") }
                }
                AdaptiveFields(12.dp) { w ->
                    Column(w(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name, { name = it }, label = { Text("Name (optional)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            host, { applyServer(it); result = "" }, label = { Text("Server (host:port)") },
                            prefix = { Text(scheme) },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(w(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            user, { user = it; result = "" }, label = { Text("Username") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            pass, { pass = it; result = "" }, label = { Text("Password") }, singleLine = true,
                            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !testing && server.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
                        onClick = {
                            scope.launch {
                                testing = true
                                result = try {
                                    Repository.checkXtream(server, user.trim(), pass.trim())
                                } catch (e: XtreamException) {
                                    e.message ?: "Error"
                                } catch (e: Exception) {
                                    "Could not reach the server: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
                                }
                                testing = false
                            }
                        },
                        modifier = Modifier.focusBorder()
                    ) { Text(if (testing) "Testing..." else "Test connection") }
                    Text(
                        result,
                        color = if (result.startsWith("Login OK")) Color(0xFF7BE07B) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    if (editIndex != null) {
                        vm.updatePlaylist(
                            editIndex,
                            Playlist(name.ifBlank { "Xtream" }, Repository.normalizeServer(server), user.trim(), pass.trim())
                        )
                    } else {
                        vm.addXtream(name, server, user, pass)
                    }
                    onClose()
                }
            }) { Text(if (editIndex != null) "Save" else "Login") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun ChangePlaylistDialog(vm: MainViewModel, onClose: () -> Unit) {
    val list by vm.playlists.collectAsStateWithLifecycle()
    val active by vm.activeIndex.collectAsStateWithLifecycle()
    var editIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideDialog,
        title = { Text("Change playlist") },
        text = {
            if (list.isEmpty()) Text("No playlists yet.")
            else LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(list.withIndex().toList(), key = { it.index }) { (i, p) ->
                    val editable = !p.url.startsWith("file://") && !com.example.familytv.data.IptvOrg.isPack(p.url)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == active, onClick = { vm.setActive(i) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, maxLines = 1)
                            Text(
                                if (p.user.isNotBlank()) "Xtream: ${p.user} @ ${p.url}" else if (com.example.familytv.data.IptvOrg.isPack(p.url)) "Auto-updating pack" else "M3U",
                                style = MaterialTheme.typography.bodySmall, maxLines = 1
                            )
                        }
                        if (editable) {
                            IconButton(onClick = { editIndex = i }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        }
                        IconButton(onClick = { vm.removePlaylist(i) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )

    editIndex?.let { i ->
        val p = list.getOrNull(i)
        if (p != null) {
            if (p.user.isNotBlank()) XtreamDialog(vm, i, p) { editIndex = null }
            else AddPlaylistDialog(vm, i, p) { editIndex = null }
        }
    }
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
