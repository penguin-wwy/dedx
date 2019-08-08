package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.OneRegisterDecodedInstruction
import com.android.dx.io.instructions.TwoRegisterDecodedInstruction
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.pass.DataFlowAnalysisPass
import com.dedx.dex.pass.DataFlowMethodInfo
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodInfo
import com.dedx.dex.struct.MethodNode
import com.dedx.tools.Configuration
import com.dedx.utils.DecodeException
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

typealias jvmOpcodes = org.objectweb.asm.Opcodes

class MethodTransformer(val mthNode: MethodNode, val clsTransformer: ClassTransformer) {

    val blockMap = HashMap<Label, BasicBlock>()
    val inst2Block = HashMap<InstNode, BasicBlock>()
    var currBlock: BasicBlock? = null
    val dexNode = mthNode.dex()
    var ropper = ReRopper(mthNode.codeSize)
    var entry: BasicBlock? = null
    var dfInfo: DataFlowMethodInfo? = null
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
        if (Configuration.optimization) {
            visitOptimization()
        } else {
            visitNormal()
        }
    }

    private fun visitNormal() {
        try {
            mthVisit.visitCode()
            visitTryCatchBlock()
            var prevLineNumber = 0
            for (inst in mthNode.codeList) {
                if (inst != null) process(inst, prevLineNumber)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun visitOptimization() {
        CFGBuildPass.visit(this)
        dfInfo = DataFlowAnalysisPass.visit(this)
        DataFlowAnalysisPass.livenessAnalyzer(dfInfo!!)
        // TODO tranform by optimize
    }

    private fun visitTryCatchBlock() {
        for (tcBlock in mthNode.tryBlockList) {
            val start = tcBlock.instList[0]
            val end = tcBlock.instList.last()
            if (start.getLabel() == null) {
                start.setLable(Label())
            }
            if (end.getLabel() == null) {
                end.setLable(Label())
            }
            for (exec in tcBlock.execHandlers) {
                val catchInst = code(exec.addr)
                if (catchInst == null) {
                    // TODO
                } else {
                    if (catchInst.getLabel() == null) {
                        catchInst.setLable(Label())
                    }
                    val startLabel = start.getLabel()?.value ?: throw DecodeException("TryCatch block empty")
                    val endLabel = end.getLabel()?.value ?: throw DecodeException("TryCatch block empty")
                    val catchLabel = catchInst.getLabel()?.value ?: throw DecodeException("TryCatch block empty")
                    for (type in exec.typeList()) {
                        mthVisit.visitTryCatchBlock(startLabel, endLabel, catchLabel, type)
                    }
                }
            }
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

    fun slotNum(regNum: Int): Int {
        if (mthNode.argsList.isEmpty()) {
            return regNum
        }
        return regNum + mthNode.ins
    }

    private fun process(inst: InstNode, prevLineNumber: Int) {
        if (inst.getLabel() != null) {
            if (inst.getLineNumber() != prevLineNumber) {
                visitLabel(inst.getLabel()?.value, inst.getLineNumber())
            } else {
                visitLabel(inst.getLabel()?.value, null)
            }
        }
        val dalvikInst = inst.instruction
        when (dalvikInst.opcode) {
            in Opcodes.CONST_4..Opcodes.CONST -> {
                visitConst(dalvikInst as OneRegisterDecodedInstruction)
            }
            in Opcodes.IF_EQ..Opcodes.IF_LE -> {

            }
            Opcodes.INVOKE_DIRECT -> {
                visitInvokeDirect(inst, InvokeType.INVOKESPECIAL)
            }
        }
    }

    private fun visitInvokeDirect(inst: InstNode, invokeType: Int) {
        val mthInfo = MethodInfo.fromDex(dexNode, inst.instruction.index)
        mthVisit.visitMethodInsn(invokeType, mthInfo.declClass.className(), mthInfo.name, mthInfo.parseSignature(), false)
    }

    private fun visitLabel(label0: Label?, lineNumber: Int?) {
        if (label0 == null) return
        mthVisit.visitLabel(label0)
        if (lineNumber != null) mthVisit.visitLineNumber(lineNumber, label0)
    }

    private fun visitConst(dalvikInst: OneRegisterDecodedInstruction) {
        when (dalvikInst.opcode) {
            Opcodes.CONST_4 -> {
                mthVisit.visitIntInsn(jvmOpcodes.BIPUSH, dalvikInst.literalByte)
            }
            Opcodes.CONST_16, Opcodes.CONST/*?*/ -> {
                mthVisit.visitIntInsn(jvmOpcodes.SIPUSH, dalvikInst.literalInt)
            }
             else -> {
                 throw DecodeException("decode in visitConst")
            }
        }
        mthVisit.visitVarInsn(jvmOpcodes.ISTORE, slotNum(dalvikInst.a))
    }

//    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction) {
//        when (dalvikInst.opcode)
//    }
    // TODO need record which type to be cmp
}