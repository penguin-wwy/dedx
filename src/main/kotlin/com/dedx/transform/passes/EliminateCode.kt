/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.transform.passes

import com.dedx.transform.InstTransformer
import com.dedx.transform.JvmInst
import com.dedx.transform.SlotInst
import java.util.Stack

object EliminateCode : Pass {
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
