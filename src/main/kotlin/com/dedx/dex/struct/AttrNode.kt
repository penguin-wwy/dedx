package com.dedx.dex.struct

enum class AttrKey {
    CONST;
}

class AttrValue(val mark: Int, val value: Any?) {

}

interface AttrNode {
    val attributes: MutableMap<AttrKey, Any>

    fun getValue(key: AttrKey) = attributes[key]

    fun setValue(key: AttrKey, value: Any) {
        attributes[key] = value
    }
}