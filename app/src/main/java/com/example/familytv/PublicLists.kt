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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.IptvOrg

/**
 * قوائم القنوات العامة المجانية المنشورة علناً من مشروع iptv-org.
 * الروابط ثابتة النمط: /countries/<رمز الدولة>.m3u و /languages/<رمز اللغة>.m3u و /categories/<التصنيف>.m3u
 */
private const val BASE = "https://iptv-org.github.io/iptv"

private data class PublicList(val label: String, val url: String)

private val countries = listOf(
    "ae" to "United Arab Emirates (الإمارات)", "sa" to "Saudi Arabia (السعودية)", "qa" to "Qatar (قطر)",
    "kw" to "Kuwait (الكويت)", "bh" to "Bahrain (البحرين)", "om" to "Oman (عُمان)",
    "eg" to "Egypt (مصر)", "jo" to "Jordan (الأردن)", "lb" to "Lebanon (لبنان)",
    "sy" to "Syria (سوريا)", "iq" to "Iraq (العراق)", "ps" to "Palestine (فلسطين)",
    "ye" to "Yemen (اليمن)", "ly" to "Libya (ليبيا)", "tn" to "Tunisia (تونس)",
    "dz" to "Algeria (الجزائر)", "ma" to "Morocco (المغرب)", "sd" to "Sudan (السودان)",
    "so" to "Somalia (الصومال)", "mr" to "Mauritania (موريتانيا)",
    "tr" to "Turkey", "ir" to "Iran", "pk" to "Pakistan", "in" to "India",
    "us" to "United States", "uk" to "United Kingdom", "ca" to "Canada",
    "fr" to "France", "de" to "Germany", "es" to "Spain", "it" to "Italy",
    "pt" to "Portugal", "nl" to "Netherlands", "br" to "Brazil", "ru" to "Russia"
).map { (code, name) -> PublicList(name, "$BASE/countries/$code.m3u") }

private val languages = listOf(
    "ara" to "Arabic (العربية)", "eng" to "English", "fra" to "French", "spa" to "Spanish",
    "deu" to "German", "tur" to "Turkish", "fas" to "Persian", "urd" to "Urdu",
    "hin" to "Hindi", "ita" to "Italian", "por" to "Portuguese", "rus" to "Russian",
    "kur" to "Kurdish"
).map { (code, name) -> PublicList(name, "$BASE/languages/$code.m3u") }

private val categories = listOf(
    "news" to "News (أخبار)", "sports" to "Sports (رياضة)", "movies" to "Movies (أفلام)",
    "series" to "Series (مسلسلات)", "kids" to "Kids (أطفال)", "music" to "Music (موسيقى)",
    "documentary" to "Documentary (وثائقي)", "education" to "Education (تعليم)",
    "religious" to "Religious (ديني)", "family" to "Family (عائلة)",
    "entertainment" to "Entertainment (ترفيه)", "general" to "General (عام)",
    "culture" to "Culture (ثقافة)", "cooking" to "Cooking (طبخ)", "travel" to "Travel (سفر)",
    "science" to "Science (علوم)", "comedy" to "Comedy (كوميديا)", "weather" to "Weather (طقس)"
).map { (id, name) -> PublicList(name, "$BASE/categories/$id.m3u") }

@Composable
fun PublicListsDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val saved by vm.playlists.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    val tabs = listOf("Country", "Language", "Category")
    val all = when (tab) { 0 -> countries; 1 -> languages; else -> categories }
    val shown = all.filter { it.label.contains(query.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Free public channels") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Publicly published lists from the iptv-org project. Some channels may be offline or geo-blocked.",
                    style = MaterialTheme.typography.bodySmall
                )
                val packAdded = saved.any { IptvOrg.isPack(it.url) }
                PillButton(
                    text = if (packAdded) "Arab + Europe pack added (updates automatically)" else "Add Arab + Europe pack (auto-update)",
                    icon = if (packAdded) null else Icons.Default.Add,
                    brush = if (packAdded) null else Pal.accentBrush,
                    enabled = !packAdded,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.addPlaylist(IptvOrg.PACK_NAME, IptvOrg.PACK_URL)
                        Toast.makeText(ctx, "Loading channels from all Arab countries, France, Spain, Italy, Belgium and the UK...", Toast.LENGTH_LONG).show()
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, t ->
                        PillButton(
                            text = t,
                            brush = if (tab == i) Pal.accentBrush else null,
                            onClick = { tab = i; query = "" }
                        )
                    }
                }
                OutlinedTextField(
                    query, { query = it }, label = { Text("Search") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(shown, key = { it.url }) { item ->
                        val added = saved.any { it.url == item.url }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, modifier = Modifier.weight(1f).padding(end = 8.dp), maxLines = 1)
                            PillButton(
                                text = if (added) "Added" else "Add",
                                icon = if (added) null else Icons.Default.Add,
                                brush = if (added) null else Pal.accentBrush,
                                enabled = !added,
                                onClick = {
                                    vm.addPlaylist(item.label.substringBefore(" ("), item.url)
                                    Toast.makeText(ctx, "Added: ${item.label}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    if (shown.isEmpty()) item { Text("No results.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
