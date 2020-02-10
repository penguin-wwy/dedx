package com.dedx.transform.passes

import com.dedx.transform.InstTransformer
import com.dedx.transform.JvmInst
import com.dedx.transform.SlotInst
import java.util.*
import kotlin.collections.ArrayList

object EliminateCodePass : Pass {
    override fun initializaPass() { }

    override fun runOnFunction(instTrans: InstTransformer) {
        eliminateLoadAndStore(instTrans)
    }

    private fun eliminateLoadAndStore(instTrans: InstTransformer) {
        val instStack = Stack<SlotInst>()
        val needToClean = ArrayList<JvmInst>()
        for (i in 0 until instTrans.instListSize()) {
            if (instTrans.inst(i) !is SlotInst) {
                continue
            }
            val slotInst = instTrans.inst(i) as SlotInst
            if (slotInst.isStoreInst()) {
                instStack.push(slotInst)
            }
            if (slotInst.isLoadInst() && instStack.isNotEmpty()) {
                if (slotInst.slot == instStack.peek().slot) {
                    needToClean.add(instStack.pop())
                    needToClean.add(slotInst)
                } else {
                    // better optmize
                    instStack.clear()
                }
            }
        }
        instTrans.removeJvmInsts(needToClean)
    }
}
