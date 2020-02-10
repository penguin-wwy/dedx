package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class InstArgNode(val regNum: Int, val type: TypeBox, inst: InstNode? = null) : AttrNode {

    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    val user = ArrayList<InstNode>()
    // method argument have no assign site
    val site = inst

    fun isArgument() = site == null

    fun setName(name: String) {
        attributes[AttrKey.NAME] = AttrValue(Enc.ENC_STRING, name)
    }

    fun getName() = attributes[AttrKey.NAME]?.getAsString()
}
