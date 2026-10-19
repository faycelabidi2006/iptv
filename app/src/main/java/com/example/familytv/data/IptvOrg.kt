package com.example.familytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * حزمة قنوات مجانية عامة من مشروع iptv-org: كل الدول العربية + فرنسا وإسبانيا وإيطاليا وبلجيكا وبريطانيا.
 * تُحمَّل كل دولة من قائمتها الرسمية وتُدمج في قائمة واحدة، والتصنيف (group) = اسم الدولة.
 * تُحدَّث عند كل تشغيل للتطبيق وكل بضع ساعات أثناء عمله، وتبقى نسخة محلية لكل دولة عند انقطاع الإنترنت.
 */
object IptvOrg {
    const val PACK_URL = "iptvorg:arab-eu"
    const val PACK_NAME = "Arab + Europe (auto-update)"
    private const val BASE = "https://iptv-org.github.io/iptv/countries"

    fun isPack(url: String) = url == PACK_URL

    private val countries = listOf(
        "ae" to "UAE · الإمارات", "sa" to "Saudi Arabia · السعودية", "qa" to "Qatar · قطر",
        "kw" to "Kuwait · الكويت", "bh" to "Bahrain · البحرين", "om" to "Oman · عُمان",
        "eg" to "Egypt · مصر", "jo" to "Jordan · الأردن", "lb" to "Lebanon · لبنان",
        "sy" to "Syria · سوريا", "iq" to "Iraq · العراق", "ps" to "Palestine · فلسطين",
        "ye" to "Yemen · اليمن", "ly" to "Libya · ليبيا", "tn" to "Tunisia · تونس",
        "dz" to "Algeria · الجزائر", "ma" to "Morocco · المغرب", "sd" to "Sudan · السودان",
        "so" to "Somalia · الصومال", "mr" to "Mauritania · موريتانيا",
        "dj" to "Djibouti · جيبوتي", "km" to "Comoros · جزر القمر",
        "fr" to "France", "es" to "Spain", "it" to "Italy", "be" to "Belgium", "uk" to "United Kingdom"
    )

    suspend fun load(ctx: Context): List<Channel> = withContext(Dispatchers.IO) {
        val gate = Semaphore(6)
        val parts = coroutineScope {
            countries.map { (code, name) ->
                async { gate.withPermit { fetchCountry(ctx, code, name) } }
            }.awaitAll()
        }
        val seen = HashSet<String>()
        val out = ArrayList<Channel>()
        for (list in parts) for (c in list) {
            if (seen.add(c.url)) out.add(c.copy(num = out.size + 1))
        }
        if (out.isEmpty()) throw IOException("No channels could be loaded")
        out
    }

    private fun fetchCountry(ctx: Context, code: String, name: String): List<Channel> {
        val cache = File(ctx.filesDir, "iptvorg_$code.m3u")
        try {
            val conn = URL("$BASE/$code.m3u").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "FamilyTvApp/1.0")
                if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                val tmp = File(ctx.filesDir, "iptvorg_$code.tmp")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(cache) // لا نستبدل النسخة القديمة إلا بعد اكتمال التحميل
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            if (!cache.exists()) return emptyList()
        }
        return try {
            cache.bufferedReader().use { M3uParser.parse(it) }
                .map { it.copy(id = "${code}_${it.id}", group = name) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
