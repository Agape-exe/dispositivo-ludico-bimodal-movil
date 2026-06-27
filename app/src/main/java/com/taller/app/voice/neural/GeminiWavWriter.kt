package com.taller.app.voice.neural

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GeminiWavWriter {
    const val DEFAULT_SAMPLE_RATE = 24000
    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16

    /** Envuelve PCM 16-bit mono en un contenedor WAV valido con el sample rate dado. */
    fun wrapPcm16Mono(pcm: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE): ByteArray {
        val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8
        val dataSize = pcm.size
        val output = ByteArrayOutputStream(44 + dataSize)

        output.writeAscii("RIFF")
        output.writeIntLe(36 + dataSize)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(CHANNELS)
        output.writeIntLe(sampleRate)
        output.writeIntLe(byteRate)
        output.writeShortLe(blockAlign)
        output.writeShortLe(BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeIntLe(dataSize)
        output.write(pcm)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }
}
