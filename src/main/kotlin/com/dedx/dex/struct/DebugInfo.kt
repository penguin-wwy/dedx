package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class NamedDebugInfo(override val value: MutableList<AttrValue> = MutableList(5) { i -> AttrValue.Empty }) :
    AttrValueList(value) {
    companion object {
        val START = 0
        val END = 1
        val REG = 2
        val NAME = 3
        val TYPE = 4
    }

    fun getStart() = value[START].getAsInt()

    fun setStart(num: Int) {
        value[START] = AttrValue(Enc.ENC_INT, num)
    }

    fun getEnd() = value[END].getAsInt()

    fun setEnd(num: Int) {
        value[END] = AttrValue(Enc.ENC_INT, num)
    }

    fun getREG() = value[REG].getAsInt()

    fun setREG(num: Int) {
        value[REG] = AttrValue(Enc.ENC_INT, num)
    }

    fun getName() = value[NAME].getAsString()

    fun setName(name: String) {
        value[NAME] = AttrValue(Enc.ENC_STRING, name)
    }

    fun getType() = value[TYPE].getAsType()

    fun setType(type: TypeBox) {
        value[TYPE] = AttrValue(Enc.ENC_TYPE, type)
    }
}
