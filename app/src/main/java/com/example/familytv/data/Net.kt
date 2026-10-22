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
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    private fun doh(url: String, vararg ips: String) = DnsOverHttps.Builder()
        .client(bootstrapClient)
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
        .build()

    private val cloudflare: DnsOverHttps by lazy { doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1") }
    private val google: DnsOverHttps by lazy { doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4") }

    /** بحث DNS مشفّر (Cloudflare ثم Google). */
    fun secureLookup(host: String): List<InetAddress> =
        try { cloudflare.lookup(host) } catch (_: Exception) { google.lookup(host) }

    private fun buildDns(): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val system = try { Dns.SYSTEM.lookup(hostname) } catch (_: UnknownHostException) { emptyList() }
            // عناوين محلية/صفرية = علامة حجب شائعة، فنتجاهلها
            val usable = system.filter { !it.isAnyLocalAddress && !it.isLoopbackAddress }
            if (usable.isNotEmpty() && !hostname.endsWith(".local")) return usable
            return try { secureLookup(hostname) } catch (_: Exception) {
                if (system.isNotEmpty()) system else throw UnknownHostException(hostname)
            }
        }
    }

    @Synchronized
    fun client(): OkHttpClient {
        cached?.let { return it }
        val b = OkHttpClient.Builder()
            .dns(buildDns())
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

    /** يحوّل الخطأ التقني إلى سبب مفهوم. */
    fun explain(e: Throwable): String {
        val msg = (e.message ?: "").trim()
        return when {
            e is UnknownHostException ->
                "DNS: the server name could not be resolved. The address may be wrong, or your internet provider blocks it."
            e is java.net.SocketTimeoutException ->
                "Timed out: the server did not answer. Usually the server IP is blocked on your network (try a VPN), or the server is down."
            e is java.net.ConnectException && msg.contains("refused", true) ->
                "Connection refused: wrong port, or the server is down."
            e is java.net.ConnectException ->
                "Could not connect to the server. The port may be wrong or blocked."
            e is javax.net.ssl.SSLException ->
                "HTTPS/certificate problem. Try http:// instead, or turn on 'Untrusted HTTPS' in Settings."
            msg.contains("reset", true) || msg.contains("unexpected end of stream", true) ->
                "Connection was cut by the network (reset). This is typical of provider blocking."
            msg.startsWith("HTTP 403") -> "Server refused the request (HTTP 403): blocked for your country/IP, or the account is not allowed."
            msg.startsWith("HTTP 404") -> "Not found (HTTP 404): wrong server address or port."
            msg.startsWith("HTTP 401") -> "Not authorized (HTTP 401): wrong username or password."
            msg.startsWith("HTTP 5") -> "The server has an internal problem ($msg). Try again later."
            msg.startsWith("HTTP") -> "Server answered with an error ($msg)."
            else -> "Could not reach the server: ${e.javaClass.simpleName} $msg".trim()
        }
    }

    /**
     * فحص مرحلي يوضح أين يتعطل الاتصال: DNS النظام، DNS المشفّر، ثم اتصال TCP بالمنفذ.
     * يُستدعى من خيط خلفي فقط.
     */
    fun diagnose(server: String): String {
        val url = try { server.trim().let { if (it.startsWith("http", true)) it else "http://$it" }.toHttpUrl() }
                  catch (_: Exception) { return "Server address is not valid." }
        val host = url.host
        val port = url.port
        val out = StringBuilder()

        val sys = try { Dns.SYSTEM.lookup(host) } catch (e: Exception) { null }
        val sysBad = sys == null || sys.isEmpty() || sys.all { it.isAnyLocalAddress || it.isLoopbackAddress }
        out.append("1) DNS (normal): ").append(
            if (sys == null) "FAILED" else if (sysBad) "suspicious answer (${sys.joinToString { it.hostAddress ?: "" }})" else "OK (${sys.first().hostAddress})"
        ).append('\n')

        val sec = try { secureLookup(host) } catch (e: Exception) { null }
        out.append("2) DNS (secure): ").append(
            if (sec == null || sec.isEmpty()) "FAILED" else "OK (${sec.first().hostAddress})"
        ).append('\n')

        val ip = (if (!sysBad) sys else sec)?.firstOrNull()
        if (ip == null) {
            out.append("3) TCP connect: skipped (no address)\n")
            out.append("=> The name cannot be resolved. Check the address.")
        } else {
            val tcp = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 8000) }
                "OK"
            } catch (e: java.net.SocketTimeoutException) { "TIMEOUT" }
            catch (e: Exception) { "FAILED (${e.message ?: e.javaClass.simpleName})" }
            out.append("3) TCP connect to ${ip.hostAddress}:$port: $tcp\n")
            out.append(
                when {
                    tcp == "OK" && sysBad -> "=> DNS is being tampered with, but the secure DNS fixes it."
                    tcp == "OK" -> "=> The server is reachable. If login still fails, check username/password."
                    tcp == "TIMEOUT" -> "=> The server IP/port does not answer from your network. This is usually a block; a VPN or proxy is needed."
                    else -> "=> The connection is refused or cut. Check the port, or the network is blocking it."
                }
            )
        }
        return out.toString()
    }
}
