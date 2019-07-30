package com.dedx.dex.pass

import com.android.dx.io.Opcodes
import com.dedx.dex.struct.InstNode
import com.dedx.transform.BasicBlock
import com.dedx.transform.MethodTransformer
import org.objectweb.asm.Label

object CFGBuildPass {
    fun visit(meth: MethodTransformer) {
        var prevInst: InstNode? = null
        val label0 = Label()
        var currBlock = meth.blockMap.getOrPut(label0) {
            return@getOrPut BasicBlock.create(label0, null)
        }

        for (inst in meth.codeList()) {
            if (inst == null) {
                continue
            }
            if (meth.inst2Block.containsKey(inst)) {
                currBlock = meth.inst2Block[inst]!!
            } else {
                meth.inst2Block[inst] = currBlock
            }
            currBlock.instList.add(inst)
            when (inst.opcode()) {
                Opcodes.GOTO, Opcodes.GOTO_16, Opcodes.GOTO_32 -> {
                    connectBlock(meth, currBlock, meth.code(inst.target())!!)
                }
                in Opcodes.IF_EQ..Opcodes.IF_LEZ -> {
                    connectBlock(meth, currBlock, meth.nextCode(inst.cursor)!!)
                    connectBlock(meth, currBlock, meth.code(inst.target())!!)
                }
                Opcodes.THROW -> {

                }
                in Opcodes.RETURN_VOID..Opcodes.RETURN_OBJECT -> {
                    meth.exits.add(currBlock)
                }
            }
            prevInst = inst
        }
    }

    fun connectBlock(meth: MethodTransformer, curr: BasicBlock, target: InstNode) {
        var targetBlock: BasicBlock?
        if (meth.inst2Block.containsKey(target)) {
            targetBlock = meth.inst2Block[target]
        } else {
            targetBlock = meth.newBlock(curr)
            meth.inst2Block[target] = targetBlock
        }
        curr.successor.add(targetBlock!!)
    }
}