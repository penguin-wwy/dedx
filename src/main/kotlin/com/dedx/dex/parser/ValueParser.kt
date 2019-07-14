package com.dedx.dex.parser

import com.android.dex.Dex
import com.android.dex.Leb128
import com.dedx.dex.struct.FieldNode

open class EncValueParser(val dex: Dex, val section: Dex.Section) {

    companion object {
        const val ENC_BYTE = 0x00
        const val ENC_SHORT = 0x02
        const val ENC_CHAR = 0x03
        const val ENC_INT = 0x04
        const val ENC_LONG = 0x06
        const val ENC_FLOAT = 0x10
        const val ENC_DOUBLE = 0x11
        const val ENC_STRING = 0x17
        const val ENC_TYPE = 0x18
        const val ENC_FIELD = 0x19
        const val ENC_ENUM = 0x1B
        const val ENC_METHOD = 0x1A
        const val ENC_ARRAY = 0x1C
        const val ENC_ANNOTATION = 0x1D
        const val ENC_NULL = 0x1E
        const val ENC_BOOLEAN = 0x1F
    }

    fun parseValue(): Any {
        val argType = readByte()
        val type = argType and 0x1F
        val arg = (argType and 0xE0) shr 5
        when (type) {
            ENC_NULL -> return
        }
    }

    private fun parseNumber(byteCount: Int, isSignExtended: Boolean, fillOnRight: Int): Long {
        var result: Long = 0
        var last: Long = 0
        for (i in 0..byteCount) {
            last = readByte().toLong()
            result = result or (last shl (i * 8))
        }
        if (fillOnRight != 0) {
            for (i in byteCount..fillOnRight) {
                result = result shl 8
            }
        } else {
            if (isSignExtended && ((last and 0x80L) != 0L)) {
                for (i in byteCount..8) {
                    result = result or (0xFFL shl (i * 8))
                }
            }
        }
        return result
    }

    private fun parseNumber(byteCount: Int, isSignExtended: Boolean) = parseNumber(byteCount, isSignExtended, 0)

    private fun parseUnsignedInt(byteCount: Int) = parseNumber(byteCount, false, 0).toInt()

    private fun readByte(): Int = section.readByte().toInt()
}

class StaticValuesParser(dex: Dex, section: Dex.Section): EncValueParser(dex, section) {
    fun processFields(fields: List<FieldNode>): Int {
        val count = Leb128.readUnsignedLeb128(section)
        for (i in 0..count) {

        }
    }
}