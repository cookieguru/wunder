package app.wunder.data

import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray?.items(): List<Any?> =
    if (this == null) emptyList() else List(length()) { i -> opt(i).takeUnless { it == JSONObject.NULL } }

fun JSONObject.doubles(key: String): List<Double?> = optJSONArray(key).items().map { (it as? Number)?.toDouble() }
fun JSONObject.ints(key: String): List<Int?> = optJSONArray(key).items().map { (it as? Number)?.toInt() }
fun JSONObject.longs(key: String): List<Long?> = optJSONArray(key).items().map { (it as? Number)?.toLong() }
fun JSONObject.strings(key: String): List<String?> = optJSONArray(key).items().map { it as? String }

fun JSONObject.double(key: String): Double? = opt(key).let { (it as? Number)?.toDouble() }
fun JSONObject.int(key: String): Int? = opt(key).let { (it as? Number)?.toInt() }
fun JSONObject.long(key: String): Long? = opt(key).let { (it as? Number)?.toLong() }
fun JSONObject.string(key: String): String? = opt(key) as? String
fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun <T> List<T>.at(i: Int?): T? = if (i == null) null else getOrNull(i)
