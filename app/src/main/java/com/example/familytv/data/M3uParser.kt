package com.example.familytv.data

import java.io.BufferedReader

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""; var logo = ""; var group = "Other"; var pending = false
        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    pending = true
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"] ?: ""
                    group = attrs["group-title"]?.ifBlank { null } ?: "Other"
                    name = line.substringAfterLast(",").trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
                }
                line.isNotEmpty() && !line.startsWith("#") && pending -> {
                    out.add(
                        Channel(
                            id = (line + name).hashCode().toString(),
                            num = out.size + 1,
                            name = name, logo = logo, url = line, group = group
                        )
                    )
                    pending = false
                }
            }
        }
        return out
    }
}
