package com.mysocial.mesh.utils

import com.mysocial.mesh.crypto.CryptoManager
import java.net.URLDecoder
import java.net.URLEncoder

data class TrustIdentity(
    val authorId: String,
    val displayName: String,
    val isValidSignature: Boolean
)

object TrustQrUtils {

    /**
     * Builds the identity QR URI payload signed with author's Ed25519 key:
     * mysocial://trust?id=<authorId>&name=<urlEncodedName>&sig=<signature>
     */
    fun createTrustPayload(
        authorId: String,
        displayName: String,
        crypto: CryptoManager
    ): String {
        val canonical = "mysocial:trust:$authorId:$displayName"
        val signature = crypto.sign(canonical)
        val encodedId = URLEncoder.encode(authorId, "UTF-8")
        val encodedName = URLEncoder.encode(displayName, "UTF-8")
        val encodedSig = URLEncoder.encode(signature, "UTF-8")
        return "mysocial://trust?id=$encodedId&name=$encodedName&sig=$encodedSig"
    }

    /**
     * Parses and verifies an incoming Identity QR code.
     */
    fun parseAndVerifyTrustPayload(qrContent: String): TrustIdentity? {
        val clean = qrContent.trim()
        if (clean.startsWith("mysocial://trust?")) {
            val query = clean.substringAfter("mysocial://trust?")
            val params = query.split("&").associate { param ->
                val eqIdx = param.indexOf('=')
                if (eqIdx != -1) {
                    param.substring(0, eqIdx) to param.substring(eqIdx + 1)
                } else {
                    param to ""
                }
            }
            val rawId = params["id"] ?: return null
            val encodedName = params["name"] ?: ""
            val encodedSig = params["sig"] ?: ""

            val id = try { URLDecoder.decode(rawId, "UTF-8") } catch (_: Exception) { rawId }
            val name = try { URLDecoder.decode(encodedName, "UTF-8") } catch (_: Exception) { encodedName }
            val sig = try { URLDecoder.decode(encodedSig, "UTF-8") } catch (_: Exception) { encodedSig }

            val canonical = "mysocial:trust:$id:$name"
            val isValid = try {
                CryptoManager.verify(authorId = id, canonicalPayload = canonical, signatureBase64 = sig)
            } catch (_: Exception) {
                false
            }
            return TrustIdentity(authorId = id, displayName = name, isValidSignature = isValid)
        } else if (clean.startsWith("mysocial://follow?")) {
            // Legacy follow URI support
            val id = clean.substringAfter("authorId=").substringBefore("&")
            if (id.isNotBlank()) {
                val formattedId = if (id.startsWith("@")) id else "@$id"
                return TrustIdentity(authorId = formattedId, displayName = "Peer", isValidSignature = false)
            }
        }
        return null
    }
}

