package es.carlospb.modoguardia.data

import android.util.Log
import es.carlospb.modoguardia.domain.Rule
import es.carlospb.modoguardia.domain.RuleType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Reglas ↔ JSON (org.json del sistema: sin dependencias extra).
 *
 * Compatible con lo guardado por versiones anteriores: los tipos WHATSAPP_GROUP / WHATSAPP_CHAT se
 * leen como GROUP / PERSON (sin app = WhatsApp, igual que antes) y el campo «groupName» como nombre.
 */
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
                    .put("name", r.name ?: JSONObject.NULL)
                    .put("conversationId", r.conversationId ?: JSONObject.NULL)
                    .put("aliases", JSONArray(r.aliases))
                    .put("numbers", JSONArray(r.numbers))
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
                val type = RuleType.fromStored(o.optString("type")) ?: return@mapNotNull null
                Rule(
                    id = o.getString("id"),
                    type = type,
                    enabled = o.optBoolean("enabled", true),
                    packageName = o.optStringOrNull("packageName"),
                    appLabel = o.optStringOrNull("appLabel"),
                    name = o.optStringOrNull("name") ?: o.optStringOrNull("groupName"),
                    conversationId = o.optStringOrNull("conversationId"),
                    aliases = o.optStrings("aliases"),
                    numbers = o.optStrings("numbers"),
                    keywords = o.optStrings("keywords"),
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
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.optStrings(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }
}
