package com.eyalm.adns.data.provider

import android.content.Context
import org.json.JSONArray

/** Guarda la lista de DNS personalizados (nombres de host) que el usuario añadió. */
object SavedCustomDnsStore {
    private const val KEY = "saved_custom_dns"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)

    fun list(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, hostname: String): List<String> {
        val current = list(context)
        if (hostname in current) return current
        return (current + hostname).also { save(context, it) }
    }

    fun remove(context: Context, hostname: String): List<String> =
        list(context).filterNot { it == hostname }.also { save(context, it) }

    private fun save(context: Context, hostnames: List<String>) {
        prefs(context).edit().putString(KEY, JSONArray(hostnames).toString()).apply()
    }
}
