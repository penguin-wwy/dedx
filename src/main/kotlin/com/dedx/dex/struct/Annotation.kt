package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

enum class Visibility {
    BUILD,
    RUNTIME,
    SYSTEM
}

class Annotation(val visibility: Visibility?, val type: TypeBox, val values: Map<String, AttrValue>) {
    fun getDefaultValue(): Any? {
        return values["value"]
    }

    fun hasVisibility() = visibility != null

    override fun toString(): String {
        val strBuilder = StringBuilder("{")
        for (entry in values) {
            strBuilder.append("${entry.key}:${entry.value},")
        }
        strBuilder.deleteCharAt(strBuilder.length - 1)
        return strBuilder.toString()
    }
}