package com.dedx.utils

import java.lang.Exception
import kotlin.collections.ArrayList
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

    companion object {
        fun sub(b1: BitArray, b2: BitArray): BitArray {
            val result = BitArray(b1.size)
            for (offset in b1._array.indices) {
                if (offset < b2._array.size) {
                    val tmp = b1._array[offset].toInt() and b2._array[offset].toInt()
                    result._array[offset] = (b1._array[offset].toInt() - tmp).toByte()
                } else {
                    result._array[offset] = b1._array[offset]
                }
            }
            return result
        }

        fun and(b1: BitArray, b2: BitArray): BitArray {
            val size = max(b1.size, b2.size)
            val result = BitArray(size)
            val offset = max(b1._array.size, b2._array.size)
            for (i in 0 until offset) {
                if ((i < b1._array.size) && (i < b2._array.size)) {
                    result._array[i] = (b1._array[i].toInt() and b2._array[i].toInt()).toByte()
                } else {
                    result._array[i] = 0
                }
            }
            return result
        }

        fun merge(b1: BitArray, b2: BitArray): BitArray {
            val dataList = ArrayList<BitArray>()
            dataList.add(b1)
            dataList.add(b2)
            return merge(dataList)
        }

        fun merge(dataList: List<BitArray>): BitArray {
            if (dataList.size == 1) {
                return dataList[0]
            }
            var result = BitArray(0)
            for (data in dataList) {
                result = BitArray.or(result, data)
            }
            return result
        }

        fun or(b1: BitArray, b2: BitArray): BitArray {
            val size = max(b1.size, b2.size)
            val result = BitArray(size)
            val offset = max(b1._array.size, b2._array.size)
            for (i in 0 until offset) {
                if ((i < b1._array.size) && (i < b2._array.size)) {
                    result._array[i] = (b1._array[i].toInt() or b2._array[i].toInt()).toByte()
                } else {
                    if (i < b1._array.size) {
                        result._array[i] = b1._array[i]
                    } else {
                        result._array[i] = b2._array[i]
                    }
                }
            }
            return result
        }

    }

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

    fun sub(other: BitArray) = BitArray.sub(this, other)

    fun and(other: BitArray) = BitArray.and(this, other)

    fun or(other: BitArray) = BitArray.or(this, other)

    fun clear() {
        for (i in 0 until size) {
            _array[i] = 0
        }
    }

    fun equal(data: Array<Byte>): Boolean {
        if (_array.size != data.size) {
            return false
        } else {
            for (offset in _array.indices) {
                if (_array[offset] != data[offset]) {
                    return false
                }
            }
        }
        return true
    }

    override fun hashCode(): Int {
        return _array.hashCode()
    }

    override fun toString(): String {
        if (size == 0) {
            return "{}"
        }
        val indexList = ArrayList<Int>()
        for (offset in _array.indices) {
            for (i in 0..7) {
                val tmp = 1 shl i
                if ((_array[offset].toInt() and tmp) != 0) {
                    if (offset == 0) {
                        indexList.add(i)
                    } else {
                        indexList.add(offset * 8 + i)
                    }
                }
            }
        }
        return "{${indexList.joinToString(" ")}}"
    }
}