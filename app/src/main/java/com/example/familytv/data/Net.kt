package com.example.familytv.data

import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** ردّ بسيط يحافظ على نفس واجهة HttpURLConnection التي يستخدمها باقي الكود. */
class NetResponse(private val resp: Response) {
    val responseCode: Int get() = resp.code
    val inputStream: InputStream get() = resp.body?.byteStream() ?: throw IOException("Empty body")
    fun getHeaderField(name: String): String? = resp.header(name)
    fun disconnect() { try { resp.close() } catch (_: Exception) {} }
}

object Net {
    /** User-Agent مقبول عند أغلب خوادم IPTV (بعضها يحجب الأسماء غير المعروفة). */
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    @Volatile private var trustAll = false
    @Volatile private var cached: OkHttpClient? = null

    /**
     * DNS: نجرّب أولاً DNS النظام. إذا فشل أو أعاد عنواناً (مثل حجب مزوّد الإنترنت)
     * نستعمل DNS مشفّر عبر HTTPS (Cloudflare ثم Google) فلا يستطيع المزوّد التلاعب بالنتيجة.
     * ملاحظة: هذا يحلّ الحجب على مستوى DNS فقط، لا الحجب على مستوى عنوان IP أو فحص الحزم.
     */
    private fun buildDns(bootstrap: OkHttpClient): Dns {
        fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder()
            .client(bootstrap)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
            .build()
        val cloudflare = doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
        val google = doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
        return object : Dns {
          override fun lookup(hostname: String): List<InetAddress> {
            val host = hostname
            val system = try { Dns.SYSTEM.lookup(host) } catch (_: UnknownHostException) { emptyList() }
            // عناوين محلية/صفرية = علامة حجب شائعة، فنتجاهلها
            val usable = system.filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
            return if (usable.isNotEmpty() && !host.endsWith(".local")) {
                usable
            } else {
                try { cloudflare.lookup(host) } catch (_: Exception) {
                    try { google.lookup(host) } catch (_: Exception) {
                        if (system.isNotEmpty()) system else throw UnknownHostException(host)
                    }
                }
            }
          }
        }
    }

    @Synchronized
    fun client(): OkHttpClient {
        cached?.let { return it }
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        val b = OkHttpClient.Builder()
            .dns(buildDns(bootstrap))
            .followRedirects(true)
            .followSslRedirects(true) // يتبع التحويل بين http و https
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
        if (trustAll) {
            @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }
        }
        return b.build().also { cached = it }
    }

    /** يفتح الاتصال (GET) ويتبع التحويلات. نفس التوقيع القديم. */
    fun open(url: String, connectMs: Int = 15_000, readMs: Int = 30_000): NetResponse {
        val c = client().newBuilder()
            .connectTimeout(connectMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        return NetResponse(c.newCall(Request.Builder().url(url).build()).execute())
    }

    /** خيار يختاره المستخدم صراحةً: قبول شهادات HTTPS غير الموثوقة. أقل أماناً، مغلق افتراضياً. */
    @Synchronized
    fun setTrustAll(allow: Boolean) {
        if (trustAll != allow) {
            trustAll = allow
            cached = null
        }
    }
}
