package com.dedx.dex.struct

import com.android.dx.io.instructions.DecodedInstruction
import org.objectweb.asm.Label

class InstNode(val cursor: Int, val instruction: DecodedInstruction) : AttrNode {
    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()

    fun opcode() = instruction.opcode

    fun target() = instruction.target

    fun setLineNumber(line: Int) {
        attributes[AttrKey.LINENUMBER] = AttrValue(Enc.ENC_INT, line)
    }

    fun getLineNumber() = attributes[AttrKey.LINENUMBER]?.getAsInt()

    fun setLable(label: Label) {
        attributes[AttrKey.LABEL] = AttrValueLabel(label)
    }

    fun getLabel() = when (attributes.containsKey(AttrKey.LABEL)) {
        true -> attributes[AttrKey.LABEL] as AttrValueLabel
        false -> null
    }

    fun getLabelOrPut() = attributes.getOrPut(AttrKey.LABEL) {
        return@getOrPut AttrValueLabel(Label())
    } as AttrValueLabel

    fun setTryEntry(block: TryCatchBlock) {
        attributes[AttrKey.TRY_ENTRY] = AttrValue(Enc.ENC_TRY_ENTRY, block)
    }

    fun getTryEntry() = attributes[AttrKey.TRY_ENTRY]?.getAsTryEntry()

    override fun toString(): String {
        return instruction.toString()
    }
}