package com.lumen.app.data.link

import android.util.Base64
import com.lumen.app.domain.AppUsage
import com.lumen.app.domain.DayStats
import java.security.KeyPair
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class PeerInfo(
    val id: String,
    val nickname: String,
    val algorithm: String,
    val publicKeyB64: String,
    val pairedAtMs: Long,
    val host: String? = null,
)

data class ChildSnapshot(
    val syncedAtMs: Long,
    val nickname: String,
    val hasAccess: Boolean,
    val today: DayStats?,
    val week: List<DayStats>,
    val limits: Map<String, Int> = emptyMap(),
    val goalMinutes: Int = 0,
)

object Pairing {
    private const val PREFIX = "lumenlink://pair?p="

    fun buildPayload(keyPair: KeyPair, nickname: String): String {
        val algorithm = if (keyPair.public.algorithm == "X25519") "X25519" else "EC"
        val json = JSONObject()
            .put("v", 1)
            .put("n", nickname)
            .put("a", algorithm)
            .put("k", LumenCrypto.encodePublic(keyPair.public))
        val text = Base64.encodeToString(
            json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP,
        )
        return PREFIX + text
    }

    fun parse(payload: String): PairingResult? {
        val cleaned = payload.filterNot { it.isWhitespace() }
        val start = cleaned.indexOf(PREFIX)
        if (start < 0) return null
        return try {
            val json = JSONObject(
                String(
                    Base64.decode(cleaned.substring(start + PREFIX.length), Base64.URL_SAFE or Base64.NO_WRAP),
                    Charsets.UTF_8,
                ),
            )
            if (json.optInt("v") != 1) return null
            val algorithm = if (json.optString("a") == "X25519") "X25519" else "EC"
            val keyB64 = json.optString("k")
            if (keyB64.isEmpty()) return null
            val public = try {
                LumenCrypto.decodePublic(algorithm, keyB64)
            } catch (e: Exception) {
                return PairingResult(null, "The code was readable but the key inside couldn't be decoded.")
            }
            PairingResult(
                PeerInfo(
                    id = LumenCrypto.shortId(LumenCrypto.publicKeyBytes(public)),
                    nickname = json.optString("n").ifEmpty { "Friend's phone" },
                    algorithm = algorithm,
                    publicKeyB64 = keyB64,
                    pairedAtMs = System.currentTimeMillis(),
                ),
                null,
            )
        } catch (e: Exception) {
            PairingResult(null, "The code is damaged or incomplete — try copying it again.")
        }
    }
}

data class PairingResult(
    val peer: PeerInfo?,
    val problem: String?,
)

data class ExportBundle(
    val peer: PeerInfo,
    val days: List<DayStats>,
)

object LinkProtocol {
    private const val MAGIC = "LUMEN1"
    private const val MAGIC_EXPORT = "LUMENX1\n"

    private const val PIN_ITERATIONS = 120_000

    fun buildExport(peer: PeerInfo, days: List<DayStats>, pin: String): ByteArray {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val key = LumenCrypto.deriveFromPin(pin, salt, PIN_ITERATIONS)
        val payload = JSONObject()
            .put("v", 1)
            .put(
                "peer",
                JSONObject()
                    .put("id", peer.id)
                    .put("n", peer.nickname)
                    .put("a", peer.algorithm)
                    .put("k", peer.publicKeyB64)
                    .put("t", peer.pairedAtMs),
            )
            .put(
                "days",
                JSONArray().apply {
                    days.forEach { put(dayJson(it)) }
                },
            )
        val sealed = LumenCrypto.seal(key, payload.toString().toByteArray(Charsets.UTF_8))
        val out = java.io.ByteArrayOutputStream()
        out.write(MAGIC_EXPORT.toByteArray(Charsets.UTF_8))
        out.write(salt)
        out.write(intToBytes(PIN_ITERATIONS))
        out.write(sealed)
        return out.toByteArray()
    }

    fun parseExport(bytes: ByteArray, pin: String): ExportBundle? = try {
        val magic = MAGIC_EXPORT.toByteArray(Charsets.UTF_8)
        if (bytes.size < magic.size + 20) return null
        if (!bytes.copyOfRange(0, magic.size).contentEquals(magic)) return null
        var offset = magic.size
        val salt = bytes.copyOfRange(offset, offset + 16)
        offset += 16
        val iterations = bytesToInt(bytes, offset)
        offset += 4
        val key = LumenCrypto.deriveFromPin(pin, salt, iterations)
        val sealed = bytes.copyOfRange(offset, bytes.size)
        val plain = LumenCrypto.open(key, sealed)
        val json = JSONObject(String(plain, Charsets.UTF_8))
        if (json.optInt("v") != 1) return null
        val peerObj = json.getJSONObject("peer")
        val peer = PeerInfo(
            id = peerObj.getString("id"),
            nickname = peerObj.optString("n").ifEmpty { "Friend's phone" },
            algorithm = peerObj.getString("a"),
            publicKeyB64 = peerObj.getString("k"),
            pairedAtMs = peerObj.optLong("t", System.currentTimeMillis()),
        )
        val days = json.optJSONArray("days")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> parseDay(arr.getJSONObject(i)) }
        } ?: emptyList()
        ExportBundle(peer, days)
    } catch (_: Exception) {
        null
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

    private fun bytesToInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    fun clientHello(clientPubB64: String): String = "$MAGIC C $clientPubB64\n"

    fun serverHello(serverPubB64: String): String = "$MAGIC S $serverPubB64\n"

    fun clientAuth(clientMacB64: String): String = "$MAGIC A $clientMacB64\n"

    fun serverAuth(serverMacB64: String): String = "$MAGIC B $serverMacB64\n"

    fun report(sealedB64: String): String = "$MAGIC R $sealedB64\n"

    fun knock(sealedB64: String): String = "$MAGIC K $sealedB64\n"

    fun knockPayload(fromNickname: String, text: String): ByteArray =
        JSONObject()
            .put("v", 1)
            .put("f", fromNickname)
            .put("t", text)
            .toString()
            .toByteArray(Charsets.UTF_8)

    fun parseKnock(plain: ByteArray): Pair<String, String>? = try {
        val json = JSONObject(String(plain, Charsets.UTF_8))
        if (json.optInt("v") != 1) return null
        val from = json.optString("f").ifEmpty { "Linked phone" }
        val text = json.optString("t")
        if (text.isBlank()) null else Pair(from, text)
    } catch (_: Exception) {
        null
    }

    fun parseHello(line: String, expected: String): String? {
        val parts = line.trim().split(' ')
        return if (parts.size == 3 && parts[0] == MAGIC && parts[1] == expected) parts[2] else null
    }

    fun authData(role: Char, aB64: String, bB64: String): ByteArray {
        val prefix = if (role == 'c') "lumen-auth-c" else "lumen-auth-s"
        return (prefix.toByteArray(Charsets.UTF_8) +
            aB64.toByteArray(Charsets.UTF_8) +
            bB64.toByteArray(Charsets.UTF_8))
    }

    fun sessionInfo(): ByteArray = "lumen-session-1".toByteArray(Charsets.UTF_8)

    fun buildSnapshot(
        nickname: String,
        hasAccess: Boolean,
        today: DayStats?,
        week: List<DayStats>,
        limits: Map<String, Int>,
        goalMinutes: Int = 0,
    ): ByteArray {
        val json = JSONObject()
            .put("v", 1)
            .put("n", nickname)
            .put("access", hasAccess)
            .put("g", goalMinutes)
            .apply {
                today?.let { put("today", dayJson(it)) }
                put(
                    "week",
                    JSONArray().apply {
                        week.forEach { put(dayJson(it)) }
                    },
                )
                if (limits.isNotEmpty()) {
                    put(
                        "limits",
                        JSONObject().apply {
                            limits.forEach { (pkg, minutes) -> put(pkg, minutes) }
                        },
                    )
                }
            }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun dayJson(day: DayStats): JSONObject = JSONObject()
        .put("d", day.date.toEpochDay())
        .put("s", day.screenTimeMs)
        .put("u", day.unlocks)
        .put("n", day.notificationCount)
        .put("f", day.focusMinutes)
        .put(
            "a",
            JSONArray().apply {
                day.apps.forEach { app ->
                    put(
                        JSONObject()
                            .put("p", app.packageName)
                            .put("l", app.label)
                            .put("s", app.timeMs)
                            .put("n", app.notifications),
                    )
                }
            },
        )

    fun parseSnapshot(bytes: ByteArray): ChildSnapshot? = try {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        if (json.optInt("v") != 1) return null
        val limits = json.optJSONObject("limits")?.let { obj ->
            buildMap {
                val names = obj.names()
                if (names != null) {
                    for (i in 0 until names.length()) {
                        val key = names.getString(i)
                        put(key, obj.optInt(key, 0))
                    }
                }
            }
        } ?: emptyMap()
        ChildSnapshot(
            syncedAtMs = System.currentTimeMillis(),
            nickname = json.optString("n"),
            hasAccess = json.optBoolean("access"),
            today = json.optJSONObject("today")?.let { parseDay(it) },
            week = json.optJSONArray("week")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> parseDay(arr.getJSONObject(i)) }
            } ?: emptyList(),
            limits = limits,
            goalMinutes = json.optInt("g", 0),
        )
    } catch (_: Exception) {
        null
    }

    fun parseDay(json: JSONObject): DayStats? = try {
        val apps = json.optJSONArray("a")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val a = arr.getJSONObject(i)
                AppUsage(
                    packageName = a.optString("p"),
                    label = a.optString("l"),
                    timeMs = a.optLong("s"),
                    notifications = a.optInt("n"),
                    hue = 0f,
                )
            }
        } ?: emptyList()
        DayStats(
            date = LocalDate.ofEpochDay(json.optLong("d")),
            screenTimeMs = json.optLong("s"),
            unlocks = json.optInt("u"),
            notificationCount = json.optInt("n"),
            focusMinutes = json.optInt("f"),
            apps = apps,
        )
    } catch (_: Exception) {
        null
    }
}