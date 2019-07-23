package com.dedx.dex.struct

import com.dedx.dex.parser.EncValueParser
import com.dedx.dex.struct.type.TypeBox
import java.util.*

object Enc {
    const val ENC_EMPTY = -1

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

enum class AttrKey {
    LINENUMBER,
    NAME,

    ANNOTATION,
    CONST;
}

open class AttrValue(val mark: Int, open val value: Any?) {
    companion object {
        val Empty = AttrValue(-1, null)
    }

    override fun toString(): String {
        return value.toString()
    }

    fun getAsInt() = when (mark) {
        Enc.ENC_INT -> value as Int
        else -> null
    }

    fun getAsString() = when (mark) {
        Enc.ENC_STRING -> value as String
        else -> null
    }

    fun getAsType() = when (mark) {
        Enc.ENC_TYPE -> value as TypeBox
        else -> null
    }
}

open class AttrValueList(override val value: List<AttrValue> = ArrayList()) : AttrValue(Enc.ENC_ARRAY, value) {
    companion object {
        val EMPTY = AttrValueList(Collections.emptyList())
    }
    override fun toString(): String {
        val strBuilder = StringBuilder("[")
        for (v in super.value as List<AttrValue>) {
            strBuilder.append("$v ")
        }
        strBuilder.deleteCharAt(strBuilder.length - 1)
        strBuilder.append("]")
        return strBuilder.toString()
    }
}

interface AttrNode {
    val attributes: MutableMap<AttrKey, AttrValue>

    fun getValue(key: AttrKey) = attributes[key]

    fun setValue(key: AttrKey, value: AttrValue) {
        attributes[key] = value
    }
}