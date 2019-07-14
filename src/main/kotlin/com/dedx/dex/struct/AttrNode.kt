package com.dedx.dex.struct

enum class AttrKey {
    CONST;
}

enum class AttrValue {
    
}

interface AttrNode {
    var attributes: MutableMap<AttrKey, Any>

    fun getValue(key: AttrKey) = attributes[key]

    fun setValue(key: AttrKey, value: Any) {
        attributes[key] = value
    }
}