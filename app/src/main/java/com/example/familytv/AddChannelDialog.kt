package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.familytv.data.Channel
import com.example.familytv.data.ChannelSearch
import kotlinx.coroutines.launch

@Composable
fun AddChannelDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    // بحث
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf(setOf<String>()) }
    // يدوي
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Add channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Search by name", brush = if (tab == 0) Pal.accentBrush else null, onClick = { tab = 0 })
                    PillButton("Manual", brush = if (tab == 1) Pal.accentBrush else null, onClick = { tab = 1 })
                }
                if (tab == 0) {
                    Text(
                        "Searches the public iptv-org database only. Some channels may be offline or geo-blocked.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        query, { query = it }, label = { Text("Channel name") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    PillButton(
                        text = if (busy) "Searching..." else "Search",
                        enabled = !busy && query.trim().length >= 2,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            busy = true; msg = null
                            scope.launch {
                                try {
                                    results = ChannelSearch.search(ctx, query)
                                    if (results.isEmpty()) msg = "No results."
                                } catch (e: Exception) {
                                    msg = e.message ?: "Search failed"
                                }
                                busy = false
                            }
                        }
                    )
                    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    LazyColumn(Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.url }) { ch ->
                            val done = ch.url in added
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(ch.name, maxLines = 1)
                                    Text(ch.group, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                PillButton(
                                    text = if (done) "Added" else "Add",
                                    icon = if (done) null else Icons.Default.Add,
                                    brush = if (done) null else Pal.accentBrush,
                                    enabled = !done,
                                    onClick = {
                                        vm.addCustomChannel(ch.name, ch.url, ch.group, ch.logo) { isNew ->
                                            toast(if (isNew) "Added to My channels" else "Already in My channels")
                                        }
                                        added = added + ch.url
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Add a channel from a stream link you are allowed to use (for example the channel's official live stream).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(link, { link = it }, label = { Text("Stream link (http/https)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(group, { group = it }, label = { Text("Group (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    PillButton(
                        text = "Add channel",
                        icon = Icons.Default.Add,
                        enabled = name.isNotBlank() && (link.trim().startsWith("http://") || link.trim().startsWith("https://")),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            vm.addCustomChannel(name, link, group, "") { isNew ->
                                toast(if (isNew) "Added to My channels" else "Already in My channels")
                            }
                            name = ""; link = ""; group = ""
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
