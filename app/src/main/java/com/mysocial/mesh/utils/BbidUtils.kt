package com.mysocial.mesh.utils

import java.security.MessageDigest

/**
 * Deterministic human-friendly Crockford Base32 BBID generator.
 * Takes the author's public key (e.g. "@D3LYRoV...=.ed25519"), strips "@" and ".ed25519",
 * computes its SHA-256 hash, extracts the first 40 bits (5 bytes), and encodes them
 * using Crockford Base32 formatted as XXXX-XXXX (e.g. "K7M9-4X2P").
 */
object BbidUtils {
    // Crockford Base32 alphabet (excludes I, L, O, U to avoid confusion)
    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun generateBbid(authorId: String): String {
        val clean = authorId.trim()
            .removePrefix("@")
            .removeSuffix(".ed25519")

        if (clean.isBlank()) return "0000-0000"

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(clean.toByteArray(Charsets.UTF_8))
        if (hash.size < 5) return "0000-0000"

        // Take first 40 bits (5 bytes)
        val b0 = hash[0].toLong() and 0xFFL
        val b1 = hash[1].toLong() and 0xFFL
        val b2 = hash[2].toLong() and 0xFFL
        val b3 = hash[3].toLong() and 0xFFL
        val b4 = hash[4].toLong() and 0xFFL

        val bits40 = (b0 shl 32) or (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4

        // 8 characters of 5 bits each (MSB to LSB)
        val sb = StringBuilder(9)
        for (i in 7 downTo 0) {
            val shift = i * 5
            val index = ((bits40 ushr shift) and 0x1FL).toInt()
            sb.append(CROCKFORD_ALPHABET[index])
            if (i == 4) {
                sb.append('-')
            }
        }
        return sb.toString()
    }
}

/**
 * Pure Kotlin extension function to convert any authorId into its deterministic 8-char BBID.
 */
fun String.toBbid(): String = BbidUtils.generateBbid(this)

/**
 * Pure Kotlin extension function returning the BBID formatted with leading '#' badge.
 */
fun String.toFormattedBbid(): String = "#${toBbid()}"

