package com.example.bizzly.security

import android.content.Context
import android.content.SharedPreferences
import com.example.bizzly.model.BusinessAccount
import com.example.bizzly.model.BusinessProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

@Serializable
data class LockState(
    val n: Int = 0,
    val u: Long = 0L,
    val s: Int = 0
)

object PinSecurity {
    private val weakPins = setOf(
        "0123", "1234", "2345", "3456", "4567", "5678", "6789",
        "3210", "4321", "5432", "6543", "7654", "8765", "9876",
        "1212", "2580", "1004"
    )
    private val random = SecureRandom()
    private const val SALT_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun isFourDigits(pin: String): Boolean = Regex("^\\d{4}$").matches(pin)

    fun isWeakPin(pin: String): Boolean {
        if (!isFourDigits(pin)) return true
        if (pin.all { it == pin[0] }) return true
        return pin in weakPins
    }

    fun generateSalt(): String {
        val sb = StringBuilder(18)
        repeat(18) {
            sb.append(SALT_CHARS[random.nextInt(SALT_CHARS.length)])
        }
        return sb.toString()
    }

    fun hashPin(salt: String, pin: String, version: Int = 2): String {
        return if (version == 2) {
            val spec = PBEKeySpec(
                pin.toCharArray(),
                ("bz1:$salt").toByteArray(StandardCharsets.UTF_8),
                150_000,
                256
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val bytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            bytesToHex(bytes)
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$salt:$pin".toByteArray(StandardCharsets.UTF_8))
            bytesToHex(digest)
        }
    }

    fun verifyPin(profile: BusinessProfile, pin: String): Boolean {
        if (profile.hash.isBlank() || profile.salt.isBlank()) return false
        val computed = hashPin(profile.salt, pin, profile.v)
        return MessageDigest.isEqual(
            computed.toByteArray(StandardCharsets.UTF_8),
            profile.hash.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun createPinHash(pin: String): Pair<String, String> {
        val salt = generateSalt()
        val hash = hashPin(salt, pin, 2)
        return salt to hash
    }

    private const val ADMIN_INSTALL_SALT = "7pifhkdln5fh3zyrpj"
    private const val ADMIN_INSTALL_HASH = "7b43fcd24be4f8534bad5dc94d94a32ad82f2811dd9a42bc9b65724554e744f5"

    fun createCredentialHash(secret: String): Pair<String, String> = createPinHash(secret)

    fun verifyCredential(salt: String, hash: String, secret: String): Boolean {
        if (hash.isBlank() || salt.isBlank() || secret.isEmpty()) return false
        val computed = hashPin(salt, secret, 2)
        return MessageDigest.isEqual(
            computed.toByteArray(StandardCharsets.UTF_8),
            hash.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun verifyAdminInstallCode(installCode: String): Boolean {
        val clean = installCode.trim().uppercase()
        if (clean.isEmpty()) return false
        return verifyCredential(ADMIN_INSTALL_SALT, ADMIN_INSTALL_HASH, clean)
    }

    fun findProfileWithSamePin(
        account: BusinessAccount,
        pin: String,
        excludeProfileId: Long
    ): BusinessProfile? {
        return account.profiles.firstOrNull { profile ->
            profile.id != excludeProfileId && profile.hash.isNotBlank() && verifyPin(profile, pin)
        }
    }

    fun h53(input: String): String {
        var a = 0xdeadbeef.toInt()
        var b = 0x41c6ce57
        for (i in input.indices) {
            val c = input[i].code
            a = (a xor c) * 2654435761L.toInt()
            b = (b xor c) * 1597334677L.toInt()
        }
        a = (a xor (a ushr 16)) * 2246822507L.toInt() xor (b xor (b ushr 13)) * 3266489909L.toInt()
        b = (b xor (b ushr 16)) * 2246822507L.toInt() xor (a xor (a ushr 13)) * 3266489909L.toInt()
        val value = 4294967296L * (2097151L and b.toLong()) + (a.toLong() and 0xffffffffL)
        return value.toString(16)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append("%02x".format(b.toInt() and 0xff))
        }
        return sb.toString()
    }
}

class LockoutTracker(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bz_lock_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun readMap(): MutableMap<String, LockState> {
        val raw = prefs.getString("locks", null) ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString<MutableMap<String, LockState>>(raw)
        }.getOrDefault(mutableMapOf())
    }

    private fun writeMap(map: Map<String, LockState>) {
        prefs.edit().putString("locks", json.encodeToString(map)).apply()
    }

    fun checkWaitMessage(key: String, nowMs: Long = System.currentTimeMillis()): String? {
        val state = readMap()[key] ?: return null
        if (state.u > nowMs) {
            val sec = ceil((state.u - nowMs) / 1000.0).toLong()
            return "Trop d’essais. Patientez $sec s."
        }
        return null
    }

    fun recordFailure(key: String, nowMs: Long = System.currentTimeMillis()): String? {
        val map = readMap()
        val cur = map[key] ?: LockState()
        val nextN = cur.n + 1
        var msg: String? = null
        val updated = if (nextN >= 5) {
            val nextS = min(cur.s + 1, 6)
            val delayMs = (30_000.0 * 2.0.pow((nextS - 1).toDouble())).toLong()
            val until = nowMs + delayMs
            val sec = ceil(delayMs / 1000.0).toLong()
            msg = "Trop d’essais. Patientez $sec s."
            LockState(n = 0, u = until, s = nextS)
        } else {
            cur.copy(n = nextN)
        }
        map[key] = updated
        writeMap(map)
        return msg
    }

    fun reset(key: String) {
        val map = readMap()
        if (map.remove(key) != null) {
            writeMap(map)
        }
    }
}
