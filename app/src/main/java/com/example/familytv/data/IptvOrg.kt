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
import java.util.concurrent.atomic.AtomicReference

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
        "km" to "Comoros · جزر القمر",
        "fr" to "France", "es" to "Spain", "it" to "Italy", "be" to "Belgium", "uk" to "United Kingdom"
    )

    /**
     * يحمّل الدول بالتوازي، ويستدعي onPartial بعد اكتمال كل دولة بالقنوات المتوفرة حتى الآن،
     * فتظهر القنوات تدريجياً بدل انتظار الجميع. الترتيب النهائي ثابت: العربية أولاً ثم الأوروبية.
     */
    suspend fun load(ctx: Context, onPartial: ((List<Channel>) -> Unit)? = null): List<Channel> =
        withContext(Dispatchers.IO) {
            val gate = Semaphore(8)
            val results = arrayOfNulls<List<Channel>>(countries.size)
            val lock = Any()
            val lastError = AtomicReference<String?>(null)
            coroutineScope {
                countries.mapIndexed { i, (code, name) ->
                    async {
                        gate.withPermit {
                            val r = fetchCountry(ctx, code, name, lastError)
                            synchronized(lock) {
                                results[i] = r
                                if (onPartial != null && r.isNotEmpty()) onPartial(merge(results))
                            }
                        }
                    }
                }.awaitAll()
            }
            val all = merge(results)
            if (all.isEmpty()) {
                throw IOException("Could not download the channel lists (${lastError.get() ?: "unknown error"}). Check the internet connection.")
            }
            all
        }

    private fun merge(results: Array<List<Channel>?>): List<Channel> {
        val seen = HashSet<String>()
        val out = ArrayList<Channel>()
        for (list in results) {
            if (list == null) continue
            for (c in list) if (seen.add(c.url)) out.add(c.copy(num = out.size + 1))
        }
        return out
    }

    /** نفس القائمة من ثلاثة نطاقات مختلفة: إن حُجب أحدها جُرّب التالي. */
    private fun sources(code: String) = listOf(
        "$BASE/$code.m3u",
        "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/$code.m3u",
        "https://cdn.jsdelivr.net/gh/iptv-org/iptv@master/streams/$code.m3u"
    )

    private fun fetchCountry(ctx: Context, code: String, name: String, lastError: AtomicReference<String?>): List<Channel> {
        val cache = File(ctx.filesDir, "iptvorg_$code.m3u")
        var ok = false
        for (src in sources(code)) {
            try {
                val conn = Net.open(src, 8_000, 15_000)
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    val tmp = File(ctx.filesDir, "iptvorg_$code.tmp")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(cache) // لا نستبدل النسخة القديمة إلا بعد اكتمال التحميل
                    ok = true
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError.set("$code: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
            }
            if (ok) break
        }
        if (!ok && !cache.exists()) return emptyList()
        return try {
            cache.bufferedReader().use { M3uParser.parse(it) }
                .map { it.copy(id = "${code}_${it.id}", group = name) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
