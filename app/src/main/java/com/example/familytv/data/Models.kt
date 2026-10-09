package com.example.familytv.data

data class Channel(
    val id: String,      // ثابت (من الرابط والاسم) لحفظ المفضلة
    val num: Int,        // رقم القناة داخل القائمة (فريد)
    val name: String,
    val logo: String,
    val url: String,
    val group: String
)

data class Playlist(val name: String, val url: String)
