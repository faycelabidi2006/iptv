package com.example.familytv.data

data class Channel(
    val id: String,          // فريد وثابت (للمفضلة والمفاتيح)
    val num: Int,            // رقم القناة للعرض (من Xtream إن وُجد)
    val name: String,
    val logo: String,
    val url: String,         // لقنوات Xtream: رابط بدون امتداد، يُضاف الامتداد وقت التشغيل
    val group: String,
    val xtream: Boolean = false
)

/** user/pass غير فارغين = حساب Xtream (url هو الخادم). وإلا فهو رابط M3U أو ملف محلي. */
/** حساب دخول للتطبيق. owner = المالك (يستطيع إدارة المستخدمين). */
data class AppUser(val name: String, val salt: String, val hash: String, val owner: Boolean = false)

data class Playlist(val name: String, val url: String, val user: String = "", val pass: String = "")
