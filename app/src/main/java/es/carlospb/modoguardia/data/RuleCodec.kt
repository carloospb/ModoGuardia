package es.carlospb.modoguardia.data

import android.util.Log
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Reglas ↔ JSON (org.json del sistema: sin dependencias extra). */
object RuleCodec {
    private const val TAG = "RuleCodec"

    fun encode(rules: List<Rule>): String {
        val array = JSONArray()
        rules.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("type", r.type.name)
                    .put("enabled", r.enabled)
                    .put("packageName", r.packageName ?: JSONObject.NULL)
                    .put("appLabel", r.appLabel ?: JSONObject.NULL)
                    .put("groupName", r.groupName ?: JSONObject.NULL)
                    .put("keywords", JSONArray(r.keywords))
                    .put("matchAll", r.matchAll)
                    .put("ignoreCase", r.ignoreCase),
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<Rule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val type = RuleType.entries.firstOrNull { it.name == o.optString("type") } ?: return@mapNotNull null
                val kw = o.optJSONArray("keywords")
                Rule(
                    id = o.getString("id"),
                    type = type,
                    enabled = o.optBoolean("enabled", true),
                    packageName = o.optStringOrNull("packageName"),
                    appLabel = o.optStringOrNull("appLabel"),
                    groupName = o.optStringOrNull("groupName"),
                    keywords = if (kw == null) emptyList() else (0 until kw.length()).map { kw.getString(it) },
                    matchAll = o.optBoolean("matchAll", false),
                    ignoreCase = o.optBoolean("ignoreCase", true),
                )
            }
        } catch (e: JSONException) {
            // No se oculta: queda en logcat y la pantalla de estado avisará de "ninguna regla activa".
            Log.e(TAG, "Reglas corruptas, se ignoran", e)
            emptyList()
        }
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}
