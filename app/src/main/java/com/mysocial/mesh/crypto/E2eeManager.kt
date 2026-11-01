package com.mysocial.mesh.crypto

import org.bouncycastle.math.ec.rfc7748.X25519
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.mysocial.mesh.model.CanonicalJson

/**
 * Wire payload for ContentType.DIRECT_MESSAGE.
 * The message plaintext is fully encrypted with AES-256-GCM using an ephemeral X25519 shared secret.
 * Intermediate mesh nodes (mules) relaying the LogEntry see only ciphertext.
 */
@Serializable
data class DirectMessagePayload(
    val messageId: String,
    val recipientId: String,
    val ephemeralPublicKeyBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val timestamp: Long
) {
    fun toJson(): String = CanonicalJson.encodeToString(this)

    companion object {
        fun fromJson(json: String): DirectMessagePayload = CanonicalJson.decodeFromString(json)
    }
}

/**
 * End-to-End Encryption (E2EE) engine for 1-on-1 private messaging.
 * Uses birational mapping between Ed25519 and Curve25519 (X25519) to perform
 * ephemeral-static Diffie-Hellman key agreement without requiring separate encryption keys,
 * combined with AES-256-GCM for authenticated symmetric encryption.
 */
object E2eeManager {
    // 2^255 - 19
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val ONE: BigInteger = BigInteger.ONE
    private val secureRandom = SecureRandom()

    /**
     * Converts an Ed25519 32-byte public key into an X25519 32-byte public key coordinate
     * via the birational map: u = (1 + y) / (1 - y) (mod 2^255 - 19).
     */
    fun ed25519PubToX25519(edPubBytes: ByteArray): ByteArray {
        require(edPubBytes.size == 32) { "Ed25519 public key must be 32 bytes" }
        val yBytes = edPubBytes.copyOf()
        // Mask out the sign bit of x (highest bit of byte 31)
        yBytes[31] = (yBytes[31].toInt() and 0x7F).toByte()

        // Little-endian byte array to BigInteger
        val y = BigInteger(1, yBytes.reversedArray())
        val num = ONE.add(y).mod(P)
        val den = ONE.subtract(y).mod(P)
        val u = num.multiply(den.modInverse(P)).mod(P)

        // BigInteger back to 32 bytes little-endian
        val uBigEndian = u.toByteArray()
        val result = ByteArray(32)
        val len = minOf(uBigEndian.size, 32)
        for (i in 0 until len) {
            val srcIdx = uBigEndian.size - 1 - i
            if (srcIdx >= 0) {
                result[i] = uBigEndian[srcIdx]
            }
        }
        return result
    }

    /**
     * Derives the Curve25519 private scalar from an Ed25519 private seed (RFC 8032).
     */
    fun ed25519PrivToX25519(edPrivSeed: ByteArray): ByteArray {
        require(edPrivSeed.size == 32) { "Ed25519 private seed must be 32 bytes" }
        val md = MessageDigest.getInstance("SHA-512")
        val h = md.digest(edPrivSeed)
        val s = h.copyOfRange(0, 32)
        s[0] = (s[0].toInt() and 248).toByte()
        s[31] = (s[31].toInt() and 127).toByte()
        s[31] = (s[31].toInt() or 64).toByte()
        return s
    }

    /**
     * Encrypts a plaintext message for the specified recipient authorId.
     */
    fun encrypt(recipientAuthorId: String, plaintext: String): DirectMessagePayload {
        val recipientPubBytes = CryptoManager.extractPublicKeyBytes(recipientAuthorId)
            ?: throw IllegalArgumentException("Invalid recipient author ID: $recipientAuthorId")

        val recipientXPub = ed25519PubToX25519(recipientPubBytes)

        // Generate ephemeral X25519 key pair
        val ephPriv = ByteArray(32)
        X25519.generatePrivateKey(secureRandom, ephPriv)
        val ephPub = ByteArray(32)
        X25519.generatePublicKey(ephPriv, 0, ephPub, 0)

        // Compute ECDH shared secret
        val sharedSecret = ByteArray(32)
        val agreementSuccess = X25519.calculateAgreement(ephPriv, 0, recipientXPub, 0, sharedSecret, 0)
        if (!agreementSuccess) {
            throw IllegalStateException("X25519 key agreement computation failed")
        }

        // Derive AES key
        val aesKey = deriveAesKey(sharedSecret)

        // Encrypt with AES-256-GCM
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return DirectMessagePayload(
            messageId = UUID.randomUUID().toString(),
            recipientId = recipientAuthorId,
            ephemeralPublicKeyBase64 = Base64.getEncoder().encodeToString(ephPub),
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Decrypts an incoming DirectMessagePayload using the local user's Ed25519 private seed.
     * Returns null if decryption fails or authentication tag is invalid.
     */
    fun decrypt(myPrivateSeed: ByteArray, payload: DirectMessagePayload): String? {
        return try {
            val myXPriv = ed25519PrivToX25519(myPrivateSeed)
            val ephPub = Base64.getDecoder().decode(payload.ephemeralPublicKeyBase64)
            if (ephPub.size != 32) return null

            val sharedSecret = ByteArray(32)
            val agreementSuccess = X25519.calculateAgreement(myXPriv, 0, ephPub, 0, sharedSecret, 0)
            if (!agreementSuccess) return null

            val aesKey = deriveAesKey(sharedSecret)
            val iv = Base64.getDecoder().decode(payload.ivBase64)
            val ciphertext = Base64.getDecoder().decode(payload.ciphertextBase64)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
            val plaintextBytes = cipher.doFinal(ciphertext)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(sharedSecret)
        md.update("mysocial:e2ee:v1:aes-256-gcm".toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}
