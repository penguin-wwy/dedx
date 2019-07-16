package com.dedx.dex.parser

import com.android.dex.Dex
import com.android.dex.Leb128
import com.dedx.dex.struct.*
import com.dedx.dex.struct.type.TypeBox
import com.dedx.utils.DecodeException

open class EncValueParser(val dex: DexNode, val section: Dex.Section) {

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

    fun parseValue(): AttrValue {
        val argType = readByte()
        val type = argType and 0x1F
        val arg = (argType and 0xE0) shr 5
        val size = arg + 1
        when (type) {
            ENC_NULL -> return AttrValue(ENC_NULL, null)

            ENC_BOOLEAN -> return AttrValue(ENC_BYTE, arg == 1)
            ENC_BYTE -> return AttrValue(ENC_BOOLEAN, section.readByte())

            ENC_SHORT -> return AttrValue(ENC_SHORT, parseNumber(size, true).toShort())
            ENC_CHAR -> return AttrValue(ENC_CHAR, parseUnsignedInt(size).toChar())
            ENC_INT -> return AttrValue(ENC_INT, parseNumber(size, true).toInt())
            ENC_LONG -> return AttrValue(ENC_LONG, parseNumber(size, true))

            ENC_FLOAT -> return AttrValue(ENC_FLOAT, Float.fromBits(parseNumber(size, false, 4).toInt()))
            ENC_DOUBLE -> return AttrValue(ENC_DOUBLE, Double.fromBits(parseNumber(size, false, 8)))

            ENC_STRING -> return AttrValue(ENC_STRING, dex.getString(parseUnsignedInt(size)))

            ENC_TYPE -> return AttrValue(ENC_TYPE, TypeBox.create(dex.getString(parseUnsignedInt(size))))

            ENC_METHOD -> return AttrValue(ENC_METHOD, MethodInfo.fromDex(dex, parseUnsignedInt(size)))

            ENC_FIELD -> return AttrValue(ENC_FIELD, FieldInfo.fromDex(dex, parseUnsignedInt(size)))
            ENC_ENUM -> return AttrValue(ENC_ENUM, FieldInfo.fromDex(dex, parseUnsignedInt(size)))

            ENC_ARRAY -> {
                val count = Leb128.readUnsignedLeb128(section)
                val values: MutableList<Any> = ArrayList(count)
                for (i in 0 until count) {
                    values.add(parseValue())
                }
                return AttrValue(ENC_ARRAY, values)
            }
            ENC_ANNOTATION -> return AttrValue(ENC_ANNOTATION, AnnotationsParser.readAnnotation(dex, section, true))

            else -> throw DecodeException("Unknown encode value type: ${type.toString(16)}")
        }
    }

    private fun parseNumber(byteCount: Int, isSignExtended: Boolean, fillOnRight: Int): Long {
        var result: Long = 0
        var last: Long = 0
        for (i in 0 until byteCount) {
            last = readByte().toLong()
            result = result or (last shl (i * 8))
        }
        if (fillOnRight != 0) {
            for (i in byteCount until fillOnRight) {
                result = result shl 8
            }
        } else {
            if (isSignExtended && ((last and 0x80L) != 0L)) {
                for (i in byteCount until 8) {
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

class StaticValuesParser(dex: DexNode, section: Dex.Section): EncValueParser(dex, section) {
    fun processFields(fields: List<FieldNode>): Int {
        val count = Leb128.readUnsignedLeb128(section)
        for (i in 0 until count) {
            val value = parseValue()
            if (i < fields.size) {
                fields[i].setValue(AttrKey.CONST, value)
            }
        }
        return count
    }
}