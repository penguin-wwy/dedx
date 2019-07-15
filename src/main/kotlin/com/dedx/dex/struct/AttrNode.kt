package com.dedx.dex.struct

import com.dedx.dex.parser.EncValueParser
import java.util.*

enum class AttrKey {
    ANNOTATION,
    CONST;
}

open class AttrValue(val mark: Int, val value: Any?) {
}

class AttrValueList(value: List<Any> = ArrayList()) : AttrValue(EncValueParser.ENC_ARRAY, value) {
    companion object {
        val EMPTY = AttrValueList(Collections.emptyList())
    }
}

interface AttrNode {
    val attributes: MutableMap<AttrKey, Any>

    fun getValue(key: AttrKey) = attributes[key]

    fun setValue(key: AttrKey, value: Any) {
        attributes[key] = value
    }
}