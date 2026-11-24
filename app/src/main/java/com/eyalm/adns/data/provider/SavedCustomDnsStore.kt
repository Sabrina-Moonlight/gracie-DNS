package com.eyalm.adns.data.provider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda la lista de DNS personalizados (nombres de host) que el usuario añadió. */
object SavedCustomDnsStore {
    private const val KEY = "saved_custom_dns"
    private const val NAMES_KEY = "saved_custom_dns_names"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("adns_settings", Context.MODE_PRIVATE)

    fun list(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, hostname: String, name: String? = null): List<String> {
        if (!name.isNullOrBlank()) setName(context, hostname, name)
        val current = list(context)
        if (hostname in current) return current
        return (current + hostname).also { save(context, it) }
    }

    fun remove(context: Context, hostname: String): List<String> {
        setName(context, hostname, null)
        return list(context).filterNot { it == hostname }.also { save(context, it) }
    }

    /** Nombre opcional de un DNS guardado (por ejemplo, el del dispositivo de ControlD). */
    fun getName(context: Context, hostname: String): String? {
        val raw = prefs(context).getString(NAMES_KEY, null) ?: return null
        return runCatching { JSONObject(raw).optString(hostname, "") }.getOrNull()?.ifBlank { null }
    }

    private fun setName(context: Context, hostname: String, name: String?) {
        val obj = runCatching { JSONObject(prefs(context).getString(NAMES_KEY, null) ?: "{}") }
            .getOrDefault(JSONObject())
        if (name.isNullOrBlank()) obj.remove(hostname) else obj.put(hostname, name.trim())
        prefs(context).edit().putString(NAMES_KEY, obj.toString()).apply()
    }

    private fun save(context: Context, hostnames: List<String>) {
        prefs(context).edit().putString(KEY, JSONArray(hostnames).toString()).apply()
    }
}
