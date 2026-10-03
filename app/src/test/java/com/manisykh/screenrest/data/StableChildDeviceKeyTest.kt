package com.manisykh.screenrest.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableChildDeviceKeyTest {
    @Test
    fun `same device and application produce the same anonymous key`() {
        val first = stableChildDeviceKey("android-device-id", "com.manisykh.screenrest")
        val second = stableChildDeviceKey("android-device-id", "com.manisykh.screenrest")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { character -> character in '0'..'9' || character in 'a'..'f' })
    }

    @Test
    fun `different devices produce different keys`() {
        val first = stableChildDeviceKey("device-a", "com.manisykh.screenrest")
        val second = stableChildDeviceKey("device-b", "com.manisykh.screenrest")

        assertNotEquals(first, second)
    }

    @Test
    fun `missing identity does not create a key`() {
        assertEquals("", stableChildDeviceKey("", "com.manisykh.screenrest"))
        assertEquals("", stableChildDeviceKey("device-a", ""))
    }
}
