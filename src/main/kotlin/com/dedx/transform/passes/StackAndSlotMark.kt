package com.dedx.transform.passes

import com.dedx.transform.InstTransformer
import org.objectweb.asm.Opcodes

object StackAndSlotMark : Pass {
    override fun initializaPass() { }

    override fun runOnFunction(instTrans: InstTransformer) {
        var maxStackSize = 0
        val slotUses = ByteArray(instTrans.mthTransformer.mthNode.regsCount) { 0 }
        for (inst in instTrans.imInstList()) {
        }
    }
}