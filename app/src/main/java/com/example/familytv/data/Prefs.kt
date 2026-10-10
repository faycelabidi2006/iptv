package com.example.familytv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_tv")

object Prefs {
    private val PLAYLISTS = stringPreferencesKey("playlists")
    private val ACTIVE = intPreferencesKey("active")
    private val FAV = stringSetPreferencesKey("favorites")
    private val HIDDEN = stringSetPreferencesKey("hidden_groups")
    private val LOCKED = stringSetPreferencesKey("locked_groups")
    private val RECENT = stringPreferencesKey("recent")
    private val PIN = stringPreferencesKey("pin")
    private val EXTERNAL = booleanPreferencesKey("external_player")
    private val FORMAT = stringPreferencesKey("stream_format")

    private fun decode(s: String?): List<Playlist> {
        if (s.isNullOrBlank()) return emptyList()
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Playlist(o.getString("name"), o.getString("url"), o.optString("user", ""), o.optString("pass", ""))
        }
    }

    private fun encode(list: List<Playlist>): String =
        JSONArray().also { a ->
            list.forEach {
                a.put(JSONObject().put("name", it.name).put("url", it.url).put("user", it.user).put("pass", it.pass))
            }
        }.toString()

    fun playlists(ctx: Context): Flow<List<Playlist>> = ctx.dataStore.data.map { decode(it[PLAYLISTS]) }
    fun activeIndex(ctx: Context): Flow<Int> = ctx.dataStore.data.map { it[ACTIVE] ?: 0 }

    suspend fun addPlaylist(ctx: Context, name: String, url: String, user: String = "", pass: String = "") {
        ctx.dataStore.edit { p ->
            val n = decode(p[PLAYLISTS]) + Playlist(name, url, user, pass)
            p[PLAYLISTS] = encode(n)
            p[ACTIVE] = n.lastIndex
        }
    }

    suspend fun setActive(ctx: Context, i: Int) { ctx.dataStore.edit { it[ACTIVE] = i } }

    suspend fun removePlaylist(ctx: Context, i: Int) {
        ctx.dataStore.edit { p ->
            val n = decode(p[PLAYLISTS]).filterIndexed { idx, _ -> idx != i }
            p[PLAYLISTS] = encode(n)
            p[ACTIVE] = (p[ACTIVE] ?: 0).coerceIn(0, maxOf(0, n.lastIndex))
        }
    }

    fun favorites(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[FAV] ?: emptySet() }
    fun hidden(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[HIDDEN] ?: emptySet() }
    fun locked(ctx: Context): Flow<Set<String>> = ctx.dataStore.data.map { it[LOCKED] ?: emptySet() }

    private suspend fun toggle(ctx: Context, key: Preferences.Key<Set<String>>, v: String) {
        ctx.dataStore.edit { p ->
            val cur = p[key] ?: emptySet()
            p[key] = if (v in cur) cur - v else cur + v
        }
    }
    suspend fun toggleFavorite(ctx: Context, id: String) = toggle(ctx, FAV, id)
    suspend fun toggleHidden(ctx: Context, g: String) = toggle(ctx, HIDDEN, g)
    suspend fun toggleLocked(ctx: Context, g: String) = toggle(ctx, LOCKED, g)

    fun recent(ctx: Context): Flow<List<String>> =
        ctx.dataStore.data.map { (it[RECENT] ?: "").split(",").filter { s -> s.isNotBlank() } }

    suspend fun addRecent(ctx: Context, id: String) {
        ctx.dataStore.edit { p ->
            val cur = (p[RECENT] ?: "").split(",").filter { it.isNotBlank() && it != id }
            p[RECENT] = (listOf(id) + cur).take(30).joinToString(",")
        }
    }
    suspend fun clearRecent(ctx: Context) { ctx.dataStore.edit { it[RECENT] = "" } }

    fun pin(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[PIN] ?: "" }
    suspend fun setPin(ctx: Context, pin: String) { ctx.dataStore.edit { it[PIN] = pin } }

    fun external(ctx: Context): Flow<Boolean> = ctx.dataStore.data.map { it[EXTERNAL] ?: false }
    suspend fun setExternal(ctx: Context, v: Boolean) { ctx.dataStore.edit { it[EXTERNAL] = v } }

    /** صيغة بث Xtream: ts أو m3u8 */
    fun format(ctx: Context): Flow<String> = ctx.dataStore.data.map { it[FORMAT] ?: "ts" }
    suspend fun setFormat(ctx: Context, v: String) { ctx.dataStore.edit { it[FORMAT] = v } }
}
