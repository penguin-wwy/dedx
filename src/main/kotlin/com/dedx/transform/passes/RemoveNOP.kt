package com.dedx.transform.passes

import com.dedx.transform.InstTransformer
import com.dedx.transform.JvmInst
import com.dedx.transform.SingleInst
import org.objectweb.asm.Opcodes

object RemoveNOP : Pass {
    override fun initializaPass() { }

    override fun runOnFunction(instTrans: InstTransformer) {
        val nopList = ArrayList<JvmInst>()
        instTrans.imInstList().forEach {
            if (it.getAs(SingleInst::class)?.opcodes == Opcodes.NOP) {
                nopList.add(it)
            }
        }
        instTrans.removeJvmInsts(nopList)
    }
}
