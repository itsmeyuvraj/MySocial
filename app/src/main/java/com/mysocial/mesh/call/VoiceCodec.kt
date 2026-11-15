package com.mysocial.mesh.call

/**
 * High-performance, zero-latency 16 kHz voice compressor based on DVI/IMA-ADPCM.
 * Compresses 20ms of 16 kHz 16-bit mono PCM (320 samples / 640 bytes)
 * down to 164 bytes (~65 kbps), easily fitting within Bluetooth LE L2CAP CoC throughput.
 */
object VoiceCodec {

    const val SAMPLES_PER_FRAME = 320 // 20ms @ 16kHz
    const val RAW_FRAME_BYTES = SAMPLES_PER_FRAME * 2 // 640 bytes
    const val ENCODED_FRAME_BYTES = 164 // 4 bytes state header + 160 bytes nibbles

    private val INDEX_TABLE = intArrayOf(
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    )

    private val STEP_TABLE = intArrayOf(
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    )

    /**
     * Encodes 320 PCM 16-bit samples (640 bytes raw little-endian) to 164 bytes ADPCM.
     */
    fun encodeFrame(rawPcm: ByteArray, offset: Int = 0): ByteArray {
        val out = ByteArray(ENCODED_FRAME_BYTES)
        if (rawPcm.size - offset < RAW_FRAME_BYTES) return out

        var valprev = 0
        var index = 0

        // Parse first sample as initial predictor
        val firstSample = ((rawPcm[offset + 1].toInt() shl 8) or (rawPcm[offset].toInt() and 0xFF)).toShort().toInt()
        valprev = firstSample

        // Header: 2 bytes initial valprev + 1 byte index + 1 byte reserved
        out[0] = (valprev and 0xFF).toByte()
        out[1] = ((valprev shr 8) and 0xFF).toByte()
        out[2] = index.toByte()
        out[3] = 0

        var outIdx = 4
        var bufferStep = false
        var prevNibble = 0

        for (i in 0 until SAMPLES_PER_FRAME) {
            val sampleIdx = offset + i * 2
            val sample = ((rawPcm[sampleIdx + 1].toInt() shl 8) or (rawPcm[sampleIdx].toInt() and 0xFF)).toShort().toInt()

            val diff = sample - valprev
            var sign = 0
            var delta = diff
            if (delta < 0) {
                sign = 8
                delta = -delta
            }

            val step = STEP_TABLE[index]
            var vpdiff = step shr 3
            var code = 0

            if (delta >= step) {
                code = code or 4
                delta -= step
                vpdiff += step
            }
            val step2 = step shr 1
            if (delta >= step2) {
                code = code or 2
                delta -= step2
                vpdiff += step2
            }
            val step4 = step shr 2
            if (delta >= step4) {
                code = code or 1
                vpdiff += step4
            }

            if (sign != 0) {
                valprev = (valprev - vpdiff).coerceIn(-32768, 32767)
            } else {
                valprev = (valprev + vpdiff).coerceIn(-32768, 32767)
            }

            code = code or sign
            index = (index + INDEX_TABLE[code]).coerceIn(0, 88)

            if (bufferStep) {
                out[outIdx++] = ((code shl 4) or (prevNibble and 0x0F)).toByte()
            } else {
                prevNibble = code
            }
            bufferStep = !bufferStep
        }
        return out
    }

    /**
     * Decodes 164 bytes ADPCM into 640 bytes (320 PCM 16-bit little-endian samples).
     */
    fun decodeFrame(encoded: ByteArray, offset: Int = 0): ByteArray {
        val out = ByteArray(RAW_FRAME_BYTES)
        if (encoded.size - offset < ENCODED_FRAME_BYTES) return out

        var valprev = ((encoded[offset + 1].toInt() shl 8) or (encoded[offset].toInt() and 0xFF)).toShort().toInt()
        var index = (encoded[offset + 2].toInt() and 0xFF).coerceIn(0, 88)

        var inIdx = offset + 4
        var outIdx = 0
        var bufferStep = false
        var currentByte = 0

        for (i in 0 until SAMPLES_PER_FRAME) {
            val delta = if (bufferStep) {
                (currentByte shr 4) and 0x0F
            } else {
                currentByte = encoded[inIdx++].toInt() and 0xFF
                currentByte and 0x0F
            }
            bufferStep = !bufferStep

            val step = STEP_TABLE[index]
            var vpdiff = step shr 3
            if ((delta and 4) != 0) vpdiff += step
            if ((delta and 2) != 0) vpdiff += (step shr 1)
            if ((delta and 1) != 0) vpdiff += (step shr 2)

            if ((delta and 8) != 0) {
                valprev = (valprev - vpdiff).coerceIn(-32768, 32767)
            } else {
                valprev = (valprev + vpdiff).coerceIn(-32768, 32767)
            }

            index = (index + INDEX_TABLE[delta]).coerceIn(0, 88)

            out[outIdx++] = (valprev and 0xFF).toByte()
            out[outIdx++] = ((valprev shr 8) and 0xFF).toByte()
        }
        return out
    }
}
