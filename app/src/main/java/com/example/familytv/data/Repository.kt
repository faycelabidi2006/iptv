package com.example.familytv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XtreamException(message: String) : Exception(message)

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

object Repository {

    fun normalizeServer(s: String): String {
        val t = s.trim().trimEnd('/')
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    }

    /** يحمّل قائمة M3U من رابط أو ملف محلي، ويحتفظ بنسخة محلية عند انقطاع الإنترنت. */
    suspend fun loadPlaylist(ctx: Context, url: String): List<Channel> = withContext(Dispatchers.IO) {
        if (IptvOrg.isPack(url)) return@withContext IptvOrg.load(ctx)
        if (url.startsWith("file://")) {
            return@withContext File(url.removePrefix("file://")).bufferedReader().use { M3uParser.parse(it) }
        }
        val cache = File(ctx.filesDir, "pl_${url.hashCode()}.m3u")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "FamilyTvApp/1.0")
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> cache.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            if (!cache.exists()) throw e
        }
        cache.bufferedReader().use { M3uParser.parse(it) }
    }

    /**
     * يتصل بواجهة Xtream (player_api.php) ويعيد القنوات المباشرة بترتيب التصنيفات
     * وأرقام القنوات كما يرسلها الخادم.
     */
    suspend fun loadXtream(server: String, user: String, pass: String): List<Channel> =
        withContext(Dispatchers.IO) {
            val base = normalizeServer(server)
            val u = Uri.encode(user)
            val p = Uri.encode(pass)
            val api = "$base/player_api.php?username=$u&password=$p"
            try {
                val ui = JSONObject(httpGet(api)).optJSONObject("user_info")
                if (ui == null || ui.optInt("auth", 0) != 1) {
                    throw XtreamException("Login failed. Check server, username and password.")
                }
                val status = ui.str("status")
                if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                    throw XtreamException("Subscription status: $status")
                }

                val catOrder = HashMap<String, Int>()
                val catName = HashMap<String, String>()
                val cats = JSONArray(httpGet("$api&action=get_live_categories"))
                for (i in 0 until cats.length()) {
                    val o = cats.getJSONObject(i)
                    val id = o.str("category_id")
                    catOrder[id] = i
                    catName[id] = o.str("category_name").ifBlank { "Other" }
                }

                val streams = JSONArray(httpGet("$api&action=get_live_streams"))
                val pairs = ArrayList<Pair<Int, Channel>>(streams.length())
                for (i in 0 until streams.length()) {
                    val o = streams.getJSONObject(i)
                    val sid = o.str("stream_id")
                    if (sid.isEmpty()) continue
                    val cid = o.str("category_id")
                    val ch = Channel(
                        id = "x$sid",
                        num = o.optInt("num", i + 1),
                        name = o.str("name").ifBlank { "Channel $sid" },
                        logo = o.str("stream_icon"),
                        url = "$base/live/$u/$p/$sid",
                        group = catName[cid] ?: "Other",
                        xtream = true
                    )
                    pairs.add((catOrder[cid] ?: Int.MAX_VALUE) to ch)
                }
                pairs.sortedWith(compareBy<Pair<Int, Channel>>({ it.first }, { it.second.num })).map { it.second }
            } catch (e: JSONException) {
                throw XtreamException("Unexpected response from the server.")
            }
        }

    /** يختبر الدخول دون تحميل القنوات، ويعيد ملخص الحساب (الحالة وتاريخ الانتهاء وعدد الاتصالات). */
    suspend fun checkXtream(server: String, user: String, pass: String): String =
        withContext(Dispatchers.IO) {
            val api = "${normalizeServer(server)}/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}"
            try {
                val ui = JSONObject(httpGet(api)).optJSONObject("user_info")
                if (ui == null || ui.optInt("auth", 0) != 1) {
                    throw XtreamException("Login failed: wrong server, username or password.")
                }
                val exp = ui.str("exp_date").toLongOrNull()
                val expText = if (exp != null) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(exp * 1000)) else "no expiry"
                "Login OK. Status: ${ui.str("status")}. Expires: $expText. Connections: ${ui.str("active_cons")}/${ui.str("max_connections")}"
            } catch (e: JSONException) {
                throw XtreamException("The server did not answer like an Xtream server. Check the address and port.")
            }
        }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "FamilyTvApp/1.0")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
