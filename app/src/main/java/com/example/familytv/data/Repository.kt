package com.example.familytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Repository {

    /** يحمّل قائمة M3U من رابط يضعه المستخدم، ويحتفظ بنسخة محلية للاستخدام عند انقطاع الإنترنت. */
    suspend fun loadPlaylist(ctx: Context, url: String): List<Channel> = withContext(Dispatchers.IO) {
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
}
