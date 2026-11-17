package io.github.jnbp.nextstop.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject

/*
 * JSON read as a stream into the objects of org.json, leaving out every value under one of the
 * names to skip, at any depth: the answer is never held whole as text, and what is not read is
 * never made. The values are what JSONObject would hold: strings, numbers (Long where whole,
 * else Double), booleans, JSONObject.NULL, objects and arrays.
 */

/** The object [reader] stands at, without the values of the names in [skip]. */
internal fun readPrunedObject(reader: JsonReader, skip: Set<String>): JSONObject {
    val result = JSONObject()
    reader.beginObject()
    while (reader.hasNext()) {
        val name = reader.nextName()
        if (name in skip) reader.skipValue() else result.put(name, readPrunedValue(reader, skip))
    }
    reader.endObject()
    return result
}

private fun readPrunedValue(reader: JsonReader, skip: Set<String>): Any = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> readPrunedObject(reader, skip)
    JsonToken.BEGIN_ARRAY -> {
        val array = JSONArray()
        reader.beginArray()
        while (reader.hasNext()) array.put(readPrunedValue(reader, skip))
        reader.endArray()
        array
    }
    JsonToken.STRING -> reader.nextString()
    JsonToken.NUMBER -> {
        val text = reader.nextString()
        text.toLongOrNull() ?: text.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() } ?: JSONObject.NULL
    }
    JsonToken.BOOLEAN -> reader.nextBoolean()
    JsonToken.NULL -> {
        reader.nextNull()
        JSONObject.NULL
    }
    else -> {
        reader.skipValue()
        JSONObject.NULL
    }
}
