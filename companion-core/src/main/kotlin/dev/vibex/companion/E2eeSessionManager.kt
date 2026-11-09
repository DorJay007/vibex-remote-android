package dev.vibex.companion

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE v1: ECDHE-P256 + HKDF-SHA256 + AES-256-GCM)
 * between the Android Companion client and a paired VibeX Host.
 */
object E2eeSessionManager {
    const val HEADER_E2EE = "x-vibex-e2ee"
    const val HEADER_CLIENT_PUB = "x-vibex-e2ee-client-pub"
    const val HEADER_SERVER_PUB = "x-vibex-e2ee-server-pub"
    const val HEADER_SESSION = "x-vibex-e2ee-session"
    const val HEADER_AUTH = "x-vibex-e2ee-auth"
    const val VERSION = "v1"

    private val HKDF_SALT = "vibex-e2ee-v1-salt".toByteArray(Charsets.UTF_8)
    private val HKDF_INFO = "vibex-e2ee-v1-aes256gcm".toByteArray(Charsets.UTF_8)

    val AAD_AUTH = "vibex-e2ee-auth-v1".toByteArray(Charsets.UTF_8)
    val AAD_REQ = "vibex-e2ee-req-v1".toByteArray(Charsets.UTF_8)
    val AAD_RESP = "vibex-e2ee-resp-v1".toByteArray(Charsets.UTF_8)
    val AAD_WS = "vibex-e2ee-ws-v1".toByteArray(Charsets.UTF_8)

    // ASN.1 DER SubjectPublicKeyInfo prefix for NIST P-256 (secp256r1) uncompressed 65-byte point
    private val P256_SPKI_PREFIX = byteArrayOf(
        0x30.toByte(), 0x59.toByte(), 0x30.toByte(), 0x13.toByte(),
        0x06.toByte(), 0x07.toByte(), 0x2a.toByte(), 0x86.toByte(),
        0x48.toByte(), 0xce.toByte(), 0x3d.toByte(), 0x02.toByte(),
        0x01.toByte(), 0x06.toByte(), 0x08.toByte(), 0x2a.toByte(),
        0x86.toByte(), 0x48.toByte(), 0xce.toByte(), 0x3d.toByte(),
        0x03.toByte(), 0x01.toByte(), 0x07.toByte(), 0x03.toByte(),
        0x42.toByte(), 0x00.toByte(),
    )

    private val secureRandom = SecureRandom()
    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    }.generateKeyPair()

    val clientPublicKeyBase64: String = run {
        val spki = keyPair.public.encoded
        val rawPoint = spki.copyOfRange(spki.size - 65, spki.size)
        Base64.getEncoder().encodeToString(rawPoint)
    }

    data class SessionState(
        val sessionId: String,
        val serverPubBase64: String,
        val aesKey: ByteArray,
    )

    private val sessionsByOrigin = ConcurrentHashMap<String, SessionState>()

    fun originKey(urlOrOrigin: String): String {
        var trimmed = urlOrOrigin.trim()
        val apiIdx = trimmed.indexOf("/api/v1")
        if (apiIdx >= 0) {
            trimmed = trimmed.substring(0, apiIdx)
        } else {
            val healthIdx = trimmed.indexOf("/health")
            if (healthIdx >= 0) {
                trimmed = trimmed.substring(0, healthIdx)
            }
        }
        return trimmed.trimEnd('/').lowercase()
    }

    fun session(urlOrOrigin: String): SessionState? =
        sessionsByOrigin[originKey(urlOrOrigin)]

    fun isE2eeActive(urlOrOrigin: String): Boolean =
        session(urlOrOrigin) != null

    fun ingestHandshake(
        urlOrOrigin: String,
        sessionId: String?,
        serverPubBase64: String?,
    ): SessionState? {
        val cleanSessionId = sessionId?.trim().orEmpty()
        val cleanServerPub = serverPubBase64?.trim().orEmpty()
        if (cleanSessionId.isEmpty() || cleanServerPub.isEmpty()) return null

        val key = originKey(urlOrOrigin)
        val existing = sessionsByOrigin[key]
        if (existing != null && existing.sessionId == cleanSessionId && existing.serverPubBase64 == cleanServerPub) {
            return existing
        }

        return runCatching {
            val rawPoint = Base64.getDecoder().decode(cleanServerPub)
            require(rawPoint.size == 65 && rawPoint[0] == 0x04.toByte()) { "Invalid P-256 uncompressed point" }
            val spki = ByteArray(P256_SPKI_PREFIX.size + rawPoint.size)
            System.arraycopy(P256_SPKI_PREFIX, 0, spki, 0, P256_SPKI_PREFIX.size)
            System.arraycopy(rawPoint, 0, spki, P256_SPKI_PREFIX.size, rawPoint.size)

            val peerPub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(keyPair.private)
            agreement.doPhase(peerPub, true)
            val sharedSecret = agreement.generateSecret()

            val aesKey = hkdfSha256(sharedSecret)
            val state = SessionState(cleanSessionId, cleanServerPub, aesKey)
            sessionsByOrigin[key] = state
            state
        }.getOrNull()
    }

    fun hkdfSha256(sharedSecret: ByteArray): ByteArray {
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(HKDF_SALT, "HmacSHA256"))
        val prk = extractMac.doFinal(sharedSecret)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update(HKDF_INFO)
        expandMac.update(0x01.toByte())
        return expandMac.doFinal()
    }

    fun encrypt(aesKey: ByteArray, aad: ByteArray, plaintext: ByteArray): String {
        val nonce = ByteArray(12)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ctAndTag = cipher.doFinal(plaintext)
        val combined = ByteArray(12 + ctAndTag.size)
        System.arraycopy(nonce, 0, combined, 0, 12)
        System.arraycopy(ctAndTag, 0, combined, 12, ctAndTag.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(aesKey: ByteArray, aad: ByteArray, encodedBase64: String): ByteArray {
        val combined = Base64.getDecoder().decode(encodedBase64.trim())
        require(combined.size >= 12 + 16) { "E2EE payload too short" }
        val nonce = combined.copyOfRange(0, 12)
        val ctAndTag = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ctAndTag)
    }

    fun encryptString(aesKey: ByteArray, aad: ByteArray, text: String): String =
        encrypt(aesKey, aad, text.toByteArray(Charsets.UTF_8))

    fun decryptString(aesKey: ByteArray, aad: ByteArray, encodedBase64: String): String =
        String(decrypt(aesKey, aad, encodedBase64), Charsets.UTF_8)
}
