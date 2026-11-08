package com.mysocial.mesh.protocol

import com.mysocial.mesh.crypto.CryptoManager
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BLE Extended Advertising Broadcast Payload (Bluetooth 5.0+).
 * Enables higher broadcast throughput on PHY LE 2M with extended secondary advertising channels.
 *
 * Wire format:
 * [Magic (2 bytes)]: 0x4D, 0x53 ('M', 'S' for MySocial)
 * [Version (1 byte)]: 0x01
 * [Flags (1 byte)]:
 *   - 0x01: HAS_PSM (4 bytes Big-Endian Int)
 *   - 0x02: HAS_NAME (1 byte length + UTF-8 bytes)
 *   - 0x04: HAS_PUBKEY (32-byte raw Ed25519 public key)
 *   - 0x08: HAS_SEQUENCE (8 bytes Big-Endian Long)
 *   - 0x10: HAS_BROADCAST_DATA (2 bytes Big-Endian Short length + bytes)
 *   - 0x20: SUPPORTS_L2CAP (Peer supports high-speed L2CAP CoC)
 *   - 0x40: SUPPORTS_2M_PHY (Peer supports Bluetooth 5.0 2M PHY)
 *
 * Payload body:
 * [PSM (4 bytes)] (if HAS_PSM)
 * [Pubkey (32 bytes)] (if HAS_PUBKEY)
 * [Sequence (8 bytes)] (if HAS_SEQUENCE)
 * [NameLen (1 byte)] + [NameBytes] (if HAS_NAME)
 * [DataLen (2 bytes)] + [DataBytes] (if HAS_BROADCAST_DATA)
 */
data class BleExtendedPayload(
    val authorPublicKey: ByteArray? = null,
    val authorId: String? = null,
    val displayName: String? = null,
    val l2capPsm: Int? = null,
    val latestSequence: Long? = null,
    val broadcastData: ByteArray? = null,
    val supportsL2cap: Boolean = true,
    val supports2MPhy: Boolean = true,
    val isLegacyFallback: Boolean = false,
    val legacyAuthorHashPrefix: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BleExtendedPayload

        if (authorPublicKey != null) {
            if (other.authorPublicKey == null) return false
            if (!authorPublicKey.contentEquals(other.authorPublicKey)) return false
        } else if (other.authorPublicKey != null) return false

        if (authorId != other.authorId) return false
        if (displayName != other.displayName) return false
        if (l2capPsm != other.l2capPsm) return false
        if (latestSequence != other.latestSequence) return false

        if (broadcastData != null) {
            if (other.broadcastData == null) return false
            if (!broadcastData.contentEquals(other.broadcastData)) return false
        } else if (other.broadcastData != null) return false

        if (supportsL2cap != other.supportsL2cap) return false
        if (supports2MPhy != other.supports2MPhy) return false
        if (isLegacyFallback != other.isLegacyFallback) return false

        if (legacyAuthorHashPrefix != null) {
            if (other.legacyAuthorHashPrefix == null) return false
            if (!legacyAuthorHashPrefix.contentEquals(other.legacyAuthorHashPrefix)) return false
        } else if (other.legacyAuthorHashPrefix != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authorPublicKey?.contentHashCode() ?: 0
        result = 31 * result + (authorId?.hashCode() ?: 0)
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + (l2capPsm ?: 0)
        result = 31 * result + (latestSequence?.hashCode() ?: 0)
        result = 31 * result + (broadcastData?.contentHashCode() ?: 0)
        result = 31 * result + supportsL2cap.hashCode()
        result = 31 * result + supports2MPhy.hashCode()
        result = 31 * result + isLegacyFallback.hashCode()
        result = 31 * result + (legacyAuthorHashPrefix?.contentHashCode() ?: 0)
        return result
    }

    /**
     * Serializes this payload into a byte array suitable for BLE advertising service data.
     * When [maxBytes] is specified, fields are safely truncated (broadcast data first, then display name)
     * so that the payload never exceeds the Bluetooth controller's advertising packet capacity.
     */
    fun encode(maxBytes: Int = Int.MAX_VALUE): ByteArray {
        val pubKey = authorPublicKey ?: authorId?.let { id ->
            try {
                CryptoManager.extractPublicKeyBytes(id)
            } catch (_: Exception) {
                null
            }
        }

        var nameBytes = displayName?.toByteArray(Charsets.UTF_8)?.let {
            if (it.size > MAX_NAME_BYTES) it.copyOf(MAX_NAME_BYTES) else it
        }

        var bData = broadcastData?.let {
            if (it.size > MAX_BROADCAST_DATA_BYTES) it.copyOf(MAX_BROADCAST_DATA_BYTES) else it
        }

        var flags = 0
        if (l2capPsm != null && l2capPsm > 0) flags = flags or FLAG_HAS_PSM
        if (nameBytes != null && nameBytes.isNotEmpty()) flags = flags or FLAG_HAS_NAME
        if (pubKey != null && pubKey.size == 32) flags = flags or FLAG_HAS_PUBKEY
        if (latestSequence != null && latestSequence >= 0) flags = flags or FLAG_HAS_SEQUENCE
        if (bData != null && bData.isNotEmpty()) flags = flags or FLAG_HAS_BROADCAST_DATA
        if (supportsL2cap) flags = flags or FLAG_SUPPORTS_L2CAP
        if (supports2MPhy) flags = flags or FLAG_SUPPORTS_2M_PHY

        var totalSize = 4 // Magic (2) + Version (1) + Flags (1)
        if ((flags and FLAG_HAS_PSM) != 0) totalSize += 4
        if ((flags and FLAG_HAS_PUBKEY) != 0) totalSize += 32
        if ((flags and FLAG_HAS_SEQUENCE) != 0) totalSize += 8
        if ((flags and FLAG_HAS_NAME) != 0) totalSize += 1 + nameBytes!!.size
        if ((flags and FLAG_HAS_BROADCAST_DATA) != 0) totalSize += 2 + bData!!.size

        // If totalSize exceeds maxBytes, gracefully trim broadcastData first
        if (totalSize > maxBytes && (flags and FLAG_HAS_BROADCAST_DATA) != 0) {
            val sizeWithoutBData = totalSize - (2 + bData!!.size)
            val allowedBData = maxBytes - sizeWithoutBData - 2
            if (allowedBData > 0) {
                bData = bData!!.copyOf(allowedBData)
                totalSize = sizeWithoutBData + 2 + bData.size
            } else {
                bData = null
                flags = flags and FLAG_HAS_BROADCAST_DATA.inv()
                totalSize = sizeWithoutBData
            }
        }

        // If still exceeds maxBytes, trim display name
        if (totalSize > maxBytes && (flags and FLAG_HAS_NAME) != 0) {
            val sizeWithoutName = totalSize - (1 + nameBytes!!.size)
            val allowedName = maxBytes - sizeWithoutName - 1
            if (allowedName > 0) {
                nameBytes = nameBytes!!.copyOf(allowedName)
                totalSize = sizeWithoutName + 1 + nameBytes.size
            } else {
                nameBytes = null
                flags = flags and FLAG_HAS_NAME.inv()
                totalSize = sizeWithoutName
            }
        }

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_BYTE_0)
        buffer.put(MAGIC_BYTE_1)
        buffer.put(PROTOCOL_VERSION)
        buffer.put(flags.toByte())

        if ((flags and FLAG_HAS_PSM) != 0) {
            buffer.putInt(l2capPsm!!)
        }
        if ((flags and FLAG_HAS_PUBKEY) != 0) {
            buffer.put(pubKey!!)
        }
        if ((flags and FLAG_HAS_SEQUENCE) != 0) {
            buffer.putLong(latestSequence!!)
        }
        if ((flags and FLAG_HAS_NAME) != 0) {
            buffer.put(nameBytes!!.size.toByte())
            buffer.put(nameBytes)
        }
        if ((flags and FLAG_HAS_BROADCAST_DATA) != 0) {
            buffer.putShort(bData!!.size.toShort())
            buffer.put(bData)
        }

        return buffer.array()
    }

    companion object {
        const val MAGIC_BYTE_0: Byte = 0x4D // 'M'
        const val MAGIC_BYTE_1: Byte = 0x53 // 'S'
        const val PROTOCOL_VERSION: Byte = 0x01

        const val FLAG_HAS_PSM: Int = 0x01
        const val FLAG_HAS_NAME: Int = 0x02
        const val FLAG_HAS_PUBKEY: Int = 0x04
        const val FLAG_HAS_SEQUENCE: Int = 0x08
        const val FLAG_HAS_BROADCAST_DATA: Int = 0x10
        const val FLAG_SUPPORTS_L2CAP: Int = 0x20
        const val FLAG_SUPPORTS_2M_PHY: Int = 0x40

        const val MAX_NAME_BYTES = 48
        const val MAX_BROADCAST_DATA_BYTES = 1024

        /**
         * Decodes a byte array from BLE advertising service data.
         * Handles both modern Extended Advertising payloads and legacy 4-byte hash fallbacks.
         */
        fun decode(bytes: ByteArray): BleExtendedPayload? {
            if (bytes.size < 4) return null

            // Check if legacy 4-byte advertisement
            if (bytes.size == 4 && (bytes[0] != MAGIC_BYTE_0 || bytes[1] != MAGIC_BYTE_1)) {
                return BleExtendedPayload(
                    isLegacyFallback = true,
                    legacyAuthorHashPrefix = bytes
                )
            }

            // Must match Extended Advertising magic header
            if (bytes[0] != MAGIC_BYTE_0 || bytes[1] != MAGIC_BYTE_1) {
                return null
            }

            val version = bytes[2]
            if (version != PROTOCOL_VERSION) {
                // Unsupported version
                return null
            }

            val flags = bytes[3].toInt() and 0xFF
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            buffer.position(4)

            var psm: Int? = null
            var pubKey: ByteArray? = null
            var authorId: String? = null
            var seq: Long? = null
            var name: String? = null
            var bData: ByteArray? = null

            try {
                if ((flags and FLAG_HAS_PSM) != 0) {
                    if (buffer.remaining() < 4) return null
                    psm = buffer.int
                }

                if ((flags and FLAG_HAS_PUBKEY) != 0) {
                    if (buffer.remaining() < 32) return null
                    pubKey = ByteArray(32)
                    buffer.get(pubKey)
                    authorId = CryptoManager.formatAuthorId(pubKey)
                }

                if ((flags and FLAG_HAS_SEQUENCE) != 0) {
                    if (buffer.remaining() < 8) return null
                    seq = buffer.long
                }

                if ((flags and FLAG_HAS_NAME) != 0) {
                    if (buffer.remaining() < 1) return null
                    val nameLen = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() < nameLen) return null
                    val nameBytes = ByteArray(nameLen)
                    buffer.get(nameBytes)
                    name = String(nameBytes, Charsets.UTF_8)
                }

                if ((flags and FLAG_HAS_BROADCAST_DATA) != 0) {
                    if (buffer.remaining() < 2) return null
                    val dataLen = buffer.short.toInt() and 0xFFFF
                    if (buffer.remaining() < dataLen) return null
                    bData = ByteArray(dataLen)
                    buffer.get(bData)
                }

                return BleExtendedPayload(
                    authorPublicKey = pubKey,
                    authorId = authorId,
                    displayName = name,
                    l2capPsm = psm,
                    latestSequence = seq,
                    broadcastData = bData,
                    supportsL2cap = (flags and FLAG_SUPPORTS_L2CAP) != 0,
                    supports2MPhy = (flags and FLAG_SUPPORTS_2M_PHY) != 0,
                    isLegacyFallback = false
                )
            } catch (_: Exception) {
                return null
            }
        }
    }
}
