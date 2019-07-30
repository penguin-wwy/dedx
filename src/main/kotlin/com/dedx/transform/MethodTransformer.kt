package com.dedx.transform

import com.android.dx.io.Opcodes
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodInfo
import com.dedx.dex.struct.MethodNode
import org.objectweb.asm.Label
import java.lang.Exception
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

object InvokeType {
    val INVOKEVIRTUAL = 182 // visitMethodInsn
    val INVOKESPECIAL = 183 // -
    val INVOKESTATIC = 184 // -
    val INVOKEINTERFACE = 185 // -
    val INVOKEDYNAMIC = 186 // visitInvokeDynamicInsn
}

class MethodTransformer(val mthNode: MethodNode, val clsTransformer: ClassTransformer) {

    val blockMap = HashMap<Label, BasicBlock>()
    val inst2Block = HashMap<InstNode, BasicBlock>()
    var currBlock: BasicBlock? = null
    val dexNode = mthNode.dex()
    var ropper = ReRopper(mthNode.codeSize)
    var entry: BasicBlock? = null
    var exits = ArrayList<BasicBlock>()

    val mthVisit = clsTransformer.classWriter.visitMethod(
            mthNode.accFlags, mthNode.mthInfo.name, mthNode.descriptor,
            null, null)

    fun visitMethod() {
        if (mthNode.noCode) {
            return
        }
        if (mthNode.debugInfoOffset != DexNode.NO_INDEX) {
            MethodDebugInfoVisitor.visitMethod(mthNode)
        }
        CFGBuildPass.visit(this)
        try {
            mthVisit.visitCode()
            val label0 = Label()
            currBlock = blockMap.getOrPut(label0) {
                visitLabel(label0)
                return@getOrPut BasicBlock.create(label0, null)
            }
            for (inst in mthNode.codeList) {

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun codeList() = mthNode.codeList

    fun code(index: Int) = mthNode.getInst(index)

    fun prevCode(index: Int) = mthNode.getPrevInst(index)

    fun nextCode(index: Int) = mthNode.getNextInst(index)

    fun newBlock(): BasicBlock {
        val label0 = Label()
        return blockMap.getOrPut(label0) {
            return@getOrPut BasicBlock.create(label0, null)
        }
    }

    fun newBlock(prev: BasicBlock): BasicBlock {
        val label0 = Label()
        return blockMap.getOrPut(label0) {
            return@getOrPut BasicBlock.create(label0, prev)
        }
    }

    private fun process(inst: InstNode) {
        when (inst.instruction.opcode) {
            Opcodes.INVOKE_DIRECT -> {
                visitInvokeDirect(inst, InvokeType.INVOKESPECIAL)
            }
        }
//        }
    }

    private fun visitInvokeDirect(inst: InstNode, invokeType: Int) {
        val mthInfo = MethodInfo.fromDex(dexNode, inst.instruction.index)
        mthVisit.visitMethodInsn(invokeType, mthInfo.declClass.className(), mthInfo.name, mthInfo.parseSignature(), false)
    }

    private fun visitLabel(label0: Label) {
        mthVisit.visitLabel(label0)
//        mthVisit.visitLineNumber()
    }
}