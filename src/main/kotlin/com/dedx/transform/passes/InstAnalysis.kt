package com.dedx.transform.passes

import com.dedx.transform.InstTransformer
import com.dedx.transform.JumpInst
import com.dedx.transform.JvmInst

object InstAnalysisPass : Pass {
    override fun initializaPass() { }

    override fun runOnFunction(instTrans: InstTransformer) {
        fl@ for (i in 0 until instTrans.instListSize()) {
            val jvmInst = instTrans.inst(i)
            if (jvmInst is JumpInst) {
                val target = jvmInst.target.inst ?: continue@fl
                if (!instTrans.jumpMap.containsKey(target)) {
                    instTrans.jumpMap[target] = ArrayList()
                }
                instTrans.jumpMap[target]!!.add(jvmInst)
            }
        }
    }
}