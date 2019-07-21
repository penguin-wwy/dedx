package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class InstArgNode(val regNum: Int, type: TypeBox, inst: InstNode? = null): AttrNode {

    override val attributes: MutableMap<AttrKey, Any> = HashMap()
    val user = ArrayList<InstNode>()
    // method argument have no assign site
    val site = inst

    fun isArgument() = site == null
}