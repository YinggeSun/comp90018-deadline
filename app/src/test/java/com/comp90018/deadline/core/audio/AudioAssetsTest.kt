package com.comp90018.deadline.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioAssetsTest {
    @Test
    fun clickIsCompleteMono44100HzSigned16BitPcm() {
        val bytes = File("src/main/res/raw/sfx_tile_click.wav").readBytes()
        val wav = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 8, wav.getInt(4))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals(16, wav.getInt(16))
        assertEquals(1, wav.getShort(20).toInt()) // PCM, signed for 16-bit samples
        assertEquals(1, wav.getShort(22).toInt())
        assertEquals(44100, wav.getInt(24))
        assertEquals(88200, wav.getInt(28))
        assertEquals(2, wav.getShort(32).toInt())
        assertEquals(16, wav.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        assertEquals(bytes.size - 44, wav.getInt(40))
        assertEquals(0, wav.getInt(40) % 2)
        wav.position(44)
        var nonSilent = false
        while (wav.hasRemaining()) nonSilent = (wav.short.toInt() != 0) || nonSilent
        assertTrue(nonSilent)
    }
}
