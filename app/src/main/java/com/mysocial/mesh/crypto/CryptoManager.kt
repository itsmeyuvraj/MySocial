package com.mysocial.mesh.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages asymmetric Ed25519 cryptographic identity, Android Keystore hardware-backed
 * encryption of the root private key, canonical signing/verification, and SHA-256 hashing.
 */
class CryptoManager private constructor(
    val authorId: String,
    val publicKeyBytes: ByteArray,
    private val privateKeyBytes: ByteArray
) {

    /**
     * Build the canonical payload string over which the author creates their cryptographic signature.
     * Format: "$authorId:$sequence:${previousHash ?: "null"}:$timestamp:$contentType:$contentJson"
     */
    fun buildCanonicalPayload(
        authorId: String,
        sequence: Long,
        previousHash: String?,
        timestamp: Long,
        contentType: String,
        contentJson: String
    ): String {
        return Companion.buildCanonicalPayload(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson
        )
    }

    /**
     * Signs the deterministic payload string with the local Ed25519 private key.
     * Returns the signature encoded in standard Base64.
     */
    fun sign(canonicalPayload: String): String {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKeyBytes, 0))
        val data = canonicalPayload.toByteArray(Charsets.UTF_8)
        signer.update(data, 0, data.size)
        val sig = signer.generateSignature()
        return Base64.getEncoder().encodeToString(sig)
    }

    /**
     * Convenience method to sign an entry's fields directly.
     */
    fun signEntry(
        sequence: Long,
        previousHash: String?,
        timestamp: Long,
        contentType: String,
        contentJson: String
    ): String {
        val payload = buildCanonicalPayload(
            authorId = authorId,
            sequence = sequence,
            previousHash = previousHash,
            timestamp = timestamp,
            contentType = contentType,
            contentJson = contentJson
        )
        return sign(payload)
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "MySocialMeshMasterKey_v1"
        private const val PREFS_NAME = "mysocial_keystore_identity"
        private const val PREF_KEY_IV = "encrypted_priv_iv"
        private const val PREF_KEY_CIPHERTEXT = "encrypted_priv_ciphertext"
        private const val PREF_KEY_PUBLIC = "public_key_base64"
        private const val PREF_KEY_AUTHOR_ID = "author_id"

        private val secureRandom = SecureRandom()

        // High-performance caches to prevent redundant BouncyCastle & Base64 allocations
        private val publicKeyBytesCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        private val publicKeyParamsCache = java.util.concurrent.ConcurrentHashMap<String, Ed25519PublicKeyParameters>()
        
        // Bounded LRU cache for already verified signatures (max 4096 entries)
        private val verifiedSignatureCache = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, Boolean>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 4096
                }
            }
        )

        private val threadLocalSha256 = ThreadLocal.withInitial {
            MessageDigest.getInstance("SHA-256")
        }

        /**
         * Builds the canonical payload string over which an Ed25519 signature is computed.
         */
        fun buildCanonicalPayload(
            authorId: String,
            sequence: Long,
            previousHash: String?,
            timestamp: Long,
            contentType: String,
            contentJson: String
        ): String {
            val prev = previousHash ?: "null"
            return "$authorId:$sequence:$prev:$timestamp:$contentType:$contentJson"
        }

        /**
         * Computes the primary key `entryHash` — SHA-256 hex of the entire signed canonical entry:
         * "$authorId:$sequence:${previousHash ?: "null"}:$timestamp:$contentType:$contentJson:$signature"
         */
        fun computeEntryHash(
            authorId: String,
            sequence: Long,
            previousHash: String?,
            timestamp: Long,
            contentType: String,
            contentJson: String,
            signature: String
        ): String {
            val prev = previousHash ?: "null"
            val fullCanonical = "$authorId:$sequence:$prev:$timestamp:$contentType:$contentJson:$signature"
            return sha256Hex(fullCanonical)
        }

        /**
         * Verifies an Ed25519 signature against an authorId and the canonical payload string.
         * Employs cached Ed25519PublicKeyParameters and a bounded verification LRU cache.
         */
        fun verify(
            authorId: String,
            canonicalPayload: String,
            signatureBase64: String
        ): Boolean {
            // Check verification cache first to avoid repetitive elliptic curve operations
            val cacheKey = "$authorId:${signatureBase64.hashCode()}:${canonicalPayload.hashCode()}"
            if (verifiedSignatureCache[cacheKey] == true) {
                return true
            }

            return try {
                val pubParams = getPublicKeyParameters(authorId) ?: return false

                val sigBytes = Base64.getDecoder().decode(signatureBase64)
                if (sigBytes.size != 64) return false

                val signer = Ed25519Signer()
                signer.init(false, pubParams)
                val data = canonicalPayload.toByteArray(Charsets.UTF_8)
                signer.update(data, 0, data.size)
                val isValid = signer.verifySignature(sigBytes)
                if (isValid) {
                    verifiedSignatureCache[cacheKey] = true
                }
                isValid
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Retrieves or constructs cached Ed25519PublicKeyParameters.
         */
        fun getPublicKeyParameters(authorId: String): Ed25519PublicKeyParameters? {
            return publicKeyParamsCache.getOrPut(authorId) {
                val pubBytes = extractPublicKeyBytes(authorId)
                if (pubBytes.size == 32) {
                    Ed25519PublicKeyParameters(pubBytes, 0)
                } else {
                    return null
                }
            }
        }

        /**
         * SHA-256 hex string calculation using reusable ThreadLocal MessageDigest.
         */
        fun sha256Hex(data: ByteArray): String {
            val digest = threadLocalSha256.get()!!
            digest.reset()
            val hash = digest.digest(data)
            val hexChars = CharArray(hash.size * 2)
            val hexArray = "0123456789abcdef".toCharArray()
            for (i in hash.indices) {
                val v = hash[i].toInt() and 0xFF
                hexChars[i * 2] = hexArray[v ushr 4]
                hexChars[i * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }

        /**
         * SHA-256 hex string calculation for UTF-8 string.
         */
        fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

        /**
         * Formats a raw 32-byte Ed25519 public key into standard `@<base64_pubkey>.ed25519`.
         */
        fun formatAuthorId(publicKeyBytes: ByteArray): String {
            val base64 = Base64.getEncoder().encodeToString(publicKeyBytes)
            return "@$base64.ed25519"
        }

        /**
         * Extracts the raw 32-byte public key from an authorId string (handles `@...ed25519` or raw base64),
         * utilizing concurrent in-memory caching to avoid Base64 decoding allocations.
         */
        fun extractPublicKeyBytes(authorId: String): ByteArray {
            return publicKeyBytesCache.getOrPut(authorId) {
                var clean = authorId.trim()
                if (clean.startsWith("@")) {
                    clean = clean.substring(1)
                }
                if (clean.endsWith(".ed25519")) {
                    clean = clean.substring(0, clean.length - 8)
                }
                Base64.getDecoder().decode(clean)
            }
        }

        /**
         * Get or generate the device's cryptographic identity using Android Keystore
         * hardware-backed AES-256-GCM encryption for the Ed25519 private seed.
         */
        fun getInstance(context: Context): CryptoManager {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingIv = prefs.getString(PREF_KEY_IV, null)
            val existingCiphertext = prefs.getString(PREF_KEY_CIPHERTEXT, null)
            val existingAuthorId = prefs.getString(PREF_KEY_AUTHOR_ID, null)
            val existingPublic = prefs.getString(PREF_KEY_PUBLIC, null)

            if (existingIv != null && existingCiphertext != null && existingAuthorId != null && existingPublic != null) {
                try {
                    val iv = Base64.getDecoder().decode(existingIv)
                    val ciphertext = Base64.getDecoder().decode(existingCiphertext)
                    val privateKeyBytes = decryptWithAndroidKeystore(iv, ciphertext)
                    val publicKeyBytes = Base64.getDecoder().decode(existingPublic)
                    return CryptoManager(existingAuthorId, publicKeyBytes, privateKeyBytes)
                } catch (e: Exception) {
                    // If decryption fails due to Keystore invalidation, regenerate
                }
            }

            // Generate new Ed25519 keypair
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(secureRandom))
            val keyPair = generator.generateKeyPair()
            val privParams = keyPair.private as Ed25519PrivateKeyParameters
            val pubParams = keyPair.public as Ed25519PublicKeyParameters

            val privateKeyBytes = privParams.encoded
            val publicKeyBytes = pubParams.encoded
            val authorId = formatAuthorId(publicKeyBytes)

            // Encrypt private key with Android Keystore AES-256-GCM
            val (iv, ciphertext) = encryptWithAndroidKeystore(privateKeyBytes)

            prefs.edit()
                .putString(PREF_KEY_IV, Base64.getEncoder().encodeToString(iv))
                .putString(PREF_KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
                .putString(PREF_KEY_PUBLIC, Base64.getEncoder().encodeToString(publicKeyBytes))
                .putString(PREF_KEY_AUTHOR_ID, authorId)
                .apply()

            return CryptoManager(authorId, publicKeyBytes, privateKeyBytes)
        }

        /**
         * Creates an in-memory CryptoManager instance for testing or deterministic seeding.
         */
        fun createInMemory(seed: ByteArray? = null): CryptoManager {
            val generator = Ed25519KeyPairGenerator()
            val rnd = if (seed != null) {
                object : SecureRandom() {
                    private var index = 0
                    override fun nextBytes(bytes: ByteArray) {
                        for (i in bytes.indices) {
                            bytes[i] = seed[(index++) % seed.size]
                        }
                    }
                }
            } else {
                secureRandom
            }
            generator.init(Ed25519KeyGenerationParameters(rnd))
            val keyPair = generator.generateKeyPair()
            val privParams = keyPair.private as Ed25519PrivateKeyParameters
            val pubParams = keyPair.public as Ed25519PublicKeyParameters

            val privateKeyBytes = privParams.encoded
            val publicKeyBytes = pubParams.encoded
            val authorId = formatAuthorId(publicKeyBytes)

            return CryptoManager(authorId, publicKeyBytes, privateKeyBytes)
        }

        private fun getOrCreateMasterKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        }

        private fun encryptWithAndroidKeystore(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            return Pair(iv, ciphertext)
        }

        private fun decryptWithAndroidKeystore(iv: ByteArray, ciphertext: ByteArray): ByteArray {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        }
    }
}
