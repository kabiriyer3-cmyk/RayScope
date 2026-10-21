package com.rayscope.app.model

import org.json.JSONObject
import java.util.UUID

data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    /** unix seconds */
    val expire: Long = 0,
    val lastUpdated: Long = 0,
    val updateIntervalH: Int = 0,
    val supportUrl: String = "",
    val announce: String = "",
    val serverCount: Int = 0,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("url", url)
        o.put("upload", upload)
        o.put("download", download)
        o.put("total", total)
        o.put("expire", expire)
        o.put("lastUpdated", lastUpdated)
        o.put("updateIntervalH", updateIntervalH)
        o.put("supportUrl", supportUrl)
        o.put("announce", announce)
        o.put("serverCount", serverCount)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): Subscription = Subscription(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            url = o.optString("url"),
            upload = o.optLong("upload", 0L),
            download = o.optLong("download", 0L),
            total = o.optLong("total", 0L),
            expire = o.optLong("expire", 0L),
            lastUpdated = o.optLong("lastUpdated", 0L),
            updateIntervalH = o.optInt("updateIntervalH", 0),
            supportUrl = o.optString("supportUrl"),
            announce = o.optString("announce"),
            serverCount = o.optInt("serverCount", 0),
        )
    }
}
