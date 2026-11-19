package io.github.jnbp.nextstop.wear.data

import org.json.JSONObject

/**
 * The phone's offer where a delay broke the chosen connection at the change ahead (#98 on the
 * phone): take [line] ([product], [color]) at [departure] from [platform] at [stop] instead.
 * [key] names the offer; the watch sends it back with [Protocol.MSG_RESCUE_SWITCH] so the phone
 * switches only to the very offer shown ("rescue" in the trip state).
 */
data class RescueOffer(
    val key: String,
    val line: String?,
    val product: String,
    val departure: Long,
    val platform: String?,
    val stop: String,
    val color: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("line", line ?: JSONObject.NULL)
        put("product", product)
        put("dep", departure)
        put("platform", platform ?: JSONObject.NULL)
        put("stop", stop)
        color?.let { put("color", it) }
    }

    companion object {
        /** Null without a key or a departure (nothing to switch to). */
        fun parse(json: JSONObject): RescueOffer? {
            val key = json.optString("key", "")
            val departure = json.optLong("dep")
            if (key.isEmpty() || departure <= 0) return null
            fun text(name: String): String? = if (json.isNull(name)) null else json.optString(name).ifEmpty { null }
            return RescueOffer(
                key = key,
                line = text("line"),
                product = json.optString("product", ""),
                departure = departure,
                platform = text("platform"),
                stop = json.optString("stop", ""),
                color = if (json.has("color") && !json.isNull("color")) json.optLong("color") else null,
            )
        }

        /** The payload of [Protocol.MSG_RESCUE_SWITCH] for [offer]. */
        fun switchPayload(offer: RescueOffer): ByteArray = JSONObject().put("key", offer.key).toString().toByteArray(Charsets.UTF_8)
    }
}
