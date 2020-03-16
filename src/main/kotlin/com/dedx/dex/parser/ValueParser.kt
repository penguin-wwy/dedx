/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.dex.parser

import com.android.dex.Dex
import com.android.dex.Leb128
import com.dedx.dex.struct.AttrKey
import com.dedx.dex.struct.AttrValue
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.Enc.ENC_ANNOTATION
import com.dedx.dex.struct.Enc.ENC_ARRAY
import com.dedx.dex.struct.Enc.ENC_BOOLEAN
import com.dedx.dex.struct.Enc.ENC_BYTE
import com.dedx.dex.struct.Enc.ENC_CHAR
import com.dedx.dex.struct.Enc.ENC_DOUBLE
import com.dedx.dex.struct.Enc.ENC_ENUM
import com.dedx.dex.struct.Enc.ENC_FIELD
import com.dedx.dex.struct.Enc.ENC_FLOAT
import com.dedx.dex.struct.Enc.ENC_INT
import com.dedx.dex.struct.Enc.ENC_LONG
import com.dedx.dex.struct.Enc.ENC_METHOD
import com.dedx.dex.struct.Enc.ENC_NULL
import com.dedx.dex.struct.Enc.ENC_SHORT
import com.dedx.dex.struct.Enc.ENC_STRING
import com.dedx.dex.struct.Enc.ENC_TYPE
import com.dedx.dex.struct.FieldInfo
import com.dedx.dex.struct.FieldNode
import com.dedx.dex.struct.MethodInfo
import com.dedx.utils.DecodeException

open class EncValueParser(val dex: DexNode, val section: Dex.Section) {

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

            ENC_TYPE -> return AttrValue(ENC_TYPE, dex.getType(parseUnsignedInt(size)))

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
            ENC_ANNOTATION -> return AttrValue(ENC_ANNOTATION, AnnotationsParser.readAnnotation(dex, section, false))

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

    private fun readByte(): Int = section.readByte().toUByte().toInt()
}

class StaticValuesParser(dex: DexNode, section: Dex.Section) : EncValueParser(dex, section) {
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
