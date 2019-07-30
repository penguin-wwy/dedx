package com.dedx.dex.struct

import com.android.dx.io.instructions.DecodedInstruction

class InstNode(val cursor: Int, val instruction: DecodedInstruction) : AttrNode {
    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()

    fun opcode() = instruction.opcode

    fun target() = instruction.target

    fun setLineNumber(line: Int) {
        attributes[AttrKey.LINENUMBER] = AttrValue(Enc.ENC_INT, line)
    }

    fun getLineNumber() = attributes[AttrKey.LINENUMBER]?.getAsInt()
}