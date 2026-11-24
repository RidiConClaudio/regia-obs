package com.regiaobs.app.obs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import kotlin.math.cbrt
import kotlin.math.log10
import kotlin.math.pow

object ObsOpCode {
    const val HELLO = 0
    const val IDENTIFY = 1
    const val IDENTIFIED = 2
    const val REIDENTIFY = 3
    const val EVENT = 5
    const val REQUEST = 6
    const val REQUEST_RESPONSE = 7
    const val REQUEST_BATCH = 8
    const val REQUEST_BATCH_RESPONSE = 9
}

object ObsEventSubs {
    const val NONE = 0
    const val GENERAL = 1 shl 0
    const val CONFIG = 1 shl 1
    const val SCENES = 1 shl 2
    const val INPUTS = 1 shl 3
    const val TRANSITIONS = 1 shl 4
    const val FILTERS = 1 shl 5
    const val OUTPUTS = 1 shl 6
    const val SCENE_ITEMS = 1 shl 7
    const val MEDIA_INPUTS = 1 shl 8
    const val VENDORS = 1 shl 9
    const val UI = 1 shl 10
    const val ALL_STANDARD = 2047
    const val INPUT_VOLUME_METERS = 1 shl 16
    const val INPUT_ACTIVE = 1 shl 17
    const val INPUT_SHOW = 1 shl 18
    const val SCENE_ITEM_TRANSFORM = 1 shl 19
    const val ALL_WITH_METERS =
        ALL_STANDARD or INPUT_VOLUME_METERS or INPUT_ACTIVE or INPUT_SHOW or SCENE_ITEM_TRANSFORM
}

object ObsCloseCode {
    const val AUTHENTICATION_FAILED = 4009
    const val SESSION_INVALIDATED = 4011
}

object ObsAuth {
    fun computeAuth(password: String, salt: String, challenge: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val encoder = Base64.getEncoder()

        val secretBytes = digest.digest((password + salt).toByteArray(StandardCharsets.UTF_8))
        val secretBase64 = encoder.encodeToString(secretBytes)

        digest.reset()
        val authBytes = digest.digest((secretBase64 + challenge).toByteArray(StandardCharsets.UTF_8))
        return encoder.encodeToString(authBytes)
    }
}

object ObsUriParser {
    fun parse(raw: String): ObsServerProfile? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val normalized = when {
            trimmed.startsWith("obsws://", ignoreCase = true) ||
                trimmed.startsWith("ws://", ignoreCase = true) ||
                trimmed.startsWith("wss://", ignoreCase = true) -> trimmed
            else -> "ws://$trimmed"
        }

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "ws"
        val host = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val port = if (uri.port != -1) uri.port else 4455
        if (port !in 1..65535) return null

        val useTls = scheme == "wss"
        val rawPath = uri.rawPath?.removePrefix("/").orEmpty()
        val password = if (scheme == "obsws" && rawPath.isNotEmpty()) {
            runCatching { URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name()) }.getOrDefault(rawPath)
        } else {
            ""
        }

        return ObsServerProfile(
            name = "OBS ($host)",
            host = host,
            port = port,
            password = password,
            useTls = useTls
        )
    }
}

object AudioCurve {
    private const val MIN_DB = -100f
    private const val SILENCE_THRESHOLD_MUL = 0.00001f

    fun deflectionToMul(deflection: Float): Float {
        val clamped = deflection.coerceIn(0f, 1f)
        return clamped * clamped * clamped
    }

    fun mulToDeflection(mul: Float): Float {
        val clamped = mul.coerceIn(0f, 1f)
        return cbrt(clamped.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun mulToDb(mul: Float): Float {
        if (mul <= SILENCE_THRESHOLD_MUL) return MIN_DB
        val db = (20.0 * log10(mul.toDouble())).toFloat()
        return db.coerceAtLeast(MIN_DB)
    }

    fun dbToMul(db: Float): Float {
        if (db <= MIN_DB) return 0f
        return 10.0.pow(db.toDouble() / 20.0).toFloat().coerceAtLeast(0f)
    }

    fun formatDb(db: Float): String {
        if (db <= -96f) return "-∞ dB"
        val cleaned = if (kotlin.math.abs(db) < 0.05f) 0f else db
        return String.format(Locale.US, "%.1f dB", cleaned)
    }
}

object ObsErrorMapper {
    fun describe(code: Int, requestType: String, comment: String?): String {
        val cleanComment = comment?.trim().orEmpty()
        return when (code) {
            500 -> "Action is already running in OBS."
            501 -> when {
                requestType.contains("Record", ignoreCase = true) ->
                    "No active recording in OBS."
                requestType.contains("Stream", ignoreCase = true) ->
                    "No active stream in OBS."
                requestType.contains("Replay", ignoreCase = true) ->
                    "Replay Buffer is not active in OBS."
                else -> "Output is not running in OBS."
            }
            502 -> "Recording is already paused."
            503 -> "Recording is not paused."
            504 -> "Feature is disabled in OBS settings (e.g. Replay Buffer)."
            505 -> "Studio Mode is already active."
            506 -> "Enable Studio Mode first in OBS (or in the Console tab) to trigger a transition."
            600 -> {
                if (cleanComment.isNotEmpty()) {
                    "Source or scene not found in OBS: $cleanComment (use Deck → ⋮ → Sync Deck with OBS)."
                } else {
                    "Resource not found in OBS (use Deck → ⋮ → Sync Deck with OBS)."
                }
            }
            604 -> "This source does not have audio tracks."
            702 -> "Invalid parameter for $requestType${if (cleanComment.isNotEmpty()) ": $cleanComment" else "."}"
            else -> cleanComment.ifEmpty { "OBS Error ($code)" }
        }
    }
}

object ObsJson {
    val codec = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun buildRequest(
        requestType: String,
        requestId: String,
        requestData: JsonObject? = null
    ): String = buildJsonObject {
        put("op", ObsOpCode.REQUEST)
        put("d", buildJsonObject {
            put("requestType", requestType)
            put("requestId", requestId)
            if (requestData != null) {
                put("requestData", requestData)
            }
        })
    }.toString()

    fun buildIdentify(
        rpcVersion: Int,
        authentication: String? = null,
        eventSubscriptions: Int = ObsEventSubs.ALL_WITH_METERS
    ): String = buildJsonObject {
        put("op", ObsOpCode.IDENTIFY)
        put("d", buildJsonObject {
            put("rpcVersion", rpcVersion)
            if (!authentication.isNullOrEmpty()) {
                put("authentication", authentication)
            }
            put("eventSubscriptions", eventSubscriptions)
        })
    }.toString()
}

fun JsonObject.str(key: String, default: String = ""): String =
    (this[key] as? JsonPrimitive)?.content ?: default

fun JsonObject.strOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.content.equals("null", ignoreCase = true) }?.content

fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

fun JsonObject.int(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: default

fun JsonObject.long(key: String, default: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: default

fun JsonObject.double(key: String, default: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: default

fun JsonObject.float(key: String, default: Float = 0f): Float =
    (this[key] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: default

fun JsonObject.obj(key: String): JsonObject? =
    runCatching { this[key]?.jsonObject }.getOrNull()

fun JsonObject.arr(key: String): JsonArray? =
    runCatching { this[key]?.jsonArray }.getOrNull()

fun JsonElement.asObjOrNull(): JsonObject? = runCatching { this.jsonObject }.getOrNull()
