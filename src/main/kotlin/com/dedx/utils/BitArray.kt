package com.dedx.utils

import java.lang.Exception
import kotlin.math.max

class BitArrayOutIndex: Exception {
    constructor(message: String): super(message)

    constructor(message: String, cause: Throwable): super(message, cause)
}

class BitArray(val size: Int) {
    val full = size % Byte.SIZE_BITS == 0
    val _array = Array<Byte>((size / Byte.SIZE_BITS) + when (full) {
        true -> 0
        false -> 1
    }) { 0 }

    fun setTrue(index: Int) {
        if (index > size) {
            throw BitArrayOutIndex("Set $index out of length $size")
        }
        val base = index / Byte.SIZE_BITS
        val offset = index % Byte.SIZE_BITS
        val tmp = _array[base].toInt()
        _array[base] = (tmp or (0x1 shl offset)).toByte()
    }

    fun setFalse(index: Int) {
        if (index > size) {
            throw BitArrayOutIndex("Set $index out of length $size")
        }
        val base = index / Byte.SIZE_BITS
        val offset = index % Byte.SIZE_BITS
        val tmp = _array[base].toInt()
        _array[base] = (tmp and (0xFF xor  (0x1 shl offset))).toByte()
    }

    fun get(index: Int): Int {
        if (index > size) {
            throw BitArrayOutIndex("Set $index out of length $size")
        }
        val base = index / Byte.SIZE_BITS
        val offset = index % Byte.SIZE_BITS
        val tmp = 0x1 shl offset
        if ((_array[base].toInt() and tmp) == 0) {
            return 0
        }
        return 1
    }

    fun and(other: BitArray): BitArray {
        val size = max(this.size, other.size)
        val result = BitArray(size)
        val offset = max(this._array.size, other._array.size)
        for (i in 0 until offset) {
            if ((i < this._array.size) and (i < other._array.size)) {
                result._array[i] = (this._array[i].toInt() and other._array[i].toInt()).toByte()
            } else {
                result._array[i] = 0
            }
        }
        return result
    }

    fun or(other: BitArray): BitArray {
        val size = max(this.size, other.size)
        val result = BitArray(size)
        val offset = max(this._array.size, other._array.size)
        for (i in 0 until offset) {
            if ((i < this._array.size) and (i < other._array.size)) {
                result._array[i] = (this._array[i].toInt() or other._array[i].toInt()).toByte()
            } else {
                if (i < this._array.size) {
                    result._array[i] = this._array[i]
                } else {
                    result._array[i] = other._array[i]
                }
            }
        }
        return result
    }
}