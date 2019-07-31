package com.dedx.utils

import org.junit.Assert.*
import org.junit.Test

class BitArrayTest {
    @Test
    fun testBitArray() {
        val b1 = BitArray(7)
        assertEquals(b1._array.size, 1)
        b1.setTrue(0)
        assertEquals(b1._array[0].toInt(), 1)
        b1.setTrue(5)
        assertEquals(b1.get(5), 1)
        b1.setFalse(5)
        assertEquals(b1.get(5), 0)
    }

    @Test
    fun testAnd() {
        val b1 = BitArray(6)
        b1.setTrue(5)
        val b2 = BitArray(16)
        b2.setTrue(5)
        b2.setTrue(15)
        val b3 = b1.and(b2)
        assertEquals(b3._array[0].toInt(), 32)
        assertEquals(b3.size, 16)
        assertEquals(b3._array.size, 2)
    }

    @Test
    fun testOr() {
        val b1 = BitArray(6)
        b1.setTrue(5)
        val b2 = BitArray(16)
        b2.setTrue(5)
        b2.setTrue(15)
        val b3 = b1.or(b2)
        assertEquals(b3._array[0].toInt(), 32)
        assertEquals(b3._array[1].toUByte(), 128.toUByte())
    }
}