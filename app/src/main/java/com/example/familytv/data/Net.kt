package com.example.familytv.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

object Net {
    private var origFactory: SSLSocketFactory? = null
    private var origVerifier: HostnameVerifier? = null

    /**
     * يفتح الاتصال ويتبع التحويلات (301/302/303/307/308) حتى عند التحويل بين http و https،
     * لأن HttpURLConnection لا يفعل ذلك وحده، وهذا يكسر روابط كثيرة.
     */
    fun open(url: String, connectMs: Int = 15_000, readMs: Int = 30_000): HttpURLConnection {
        var target = url
        repeat(6) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.setRequestProperty("User-Agent", "FamilyTvApp/1.0")
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw IOException("Redirect without a location")
                target = URL(URL(target), loc).toString()
            } else {
                return c
            }
        }
        throw IOException("Too many redirects")
    }

    /**
     * خيار يختاره المستخدم صراحةً: قبول شهادات HTTPS غير الموثوقة أو المنتهية (يفيد مع بعض الخوادم
     * وبعض أجهزة التلفاز ذات التاريخ الخاطئ). يؤثر على تحميل القوائم وعلى مشغّل البث.
     * وهو أقل أماناً، لذلك يبقى مغلقاً افتراضياً.
     */
    @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager", "BadHostnameVerifier")
    @Synchronized
    fun setTrustAll(allow: Boolean) {
        if (origFactory == null) {
            origFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            origVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        }
        if (allow) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } else {
            origFactory?.let { HttpsURLConnection.setDefaultSSLSocketFactory(it) }
            origVerifier?.let { HttpsURLConnection.setDefaultHostnameVerifier(it) }
        }
    }
}
