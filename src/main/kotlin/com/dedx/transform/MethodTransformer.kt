package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.android.dx.io.instructions.OneRegisterDecodedInstruction
import com.android.dx.io.instructions.TwoRegisterDecodedInstruction
import com.android.dx.io.instructions.ZeroRegisterDecodedInstruction
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.pass.DataFlowAnalysisPass
import com.dedx.dex.pass.DataFlowMethodInfo
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodInfo
import com.dedx.dex.struct.MethodNode
import com.dedx.tools.Configuration
import com.dedx.utils.DecodeException
import com.sun.org.apache.bcel.internal.generic.INVOKESTATIC
import com.sun.org.apache.bcel.internal.generic.IRETURN
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

            StackFrame.initInstFrame(mthNode)
            val entryFrame = StackFrame.getFrameOrPut(0)
            for (type in mthNode.argsList) {
                entryFrame.setSlot(type.regNum, SlotType.convert(type.type)!!)
            }

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
        val frame = StackFrame.getFrameOrPut(inst.cursor).merge()
        val dalvikInst = inst.instruction
        // mark last time invoke-kind's return result
        var newestReturn: SlotType? = null
        when (dalvikInst.opcode) {
            in Opcodes.MOVE_RESULT..Opcodes.MOVE_RESULT_OBJECT -> {
                if (newestReturn != null) {
                    visitStore(newestReturn, dalvikInst.a, frame)
                }
            }
            Opcodes.RETURN_VOID -> {
                visitReturnVoid()
            }
            in Opcodes.RETURN..Opcodes.RETURN_OBJECT -> {
                visitReturn(dalvikInst.a, frame)
            }
            in Opcodes.CONST_4..Opcodes.CONST -> {
                visitConst(dalvikInst as OneRegisterDecodedInstruction, frame)
            }
            in Opcodes.GOTO..Opcodes.GOTO_32 -> {
                visitGoto(dalvikInst as ZeroRegisterDecodedInstruction)
            }
            in Opcodes.IF_EQ..Opcodes.IF_LE -> {
                visitIfStmt(dalvikInst as TwoRegisterDecodedInstruction, frame)
            }
            in Opcodes.IF_EQZ..Opcodes.IF_LEZ -> {
                visitIfStmt(dalvikInst as OneRegisterDecodedInstruction, frame)
            }
            Opcodes.INVOKE_VIRTUAL -> {
                newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKEVIRTUAL, frame)
            }
            Opcodes.INVOKE_SUPER -> {
                // TODO
            }
            Opcodes.INVOKE_DIRECT -> {
                newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESPECIAL, frame)
            }
            Opcodes.INVOKE_STATIC -> {
                newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESTATIC, frame)
            }
            Opcodes.INVOKE_INTERFACE -> {
                // TODO
            }
        }
    }

    private fun visitInvoke(dalvikInst: DecodedInstruction, invokeType: Int, frame: StackFrame): SlotType? {
        val mthInfo = MethodInfo.fromDex(dexNode, dalvikInst.index)
        for (i in 0..dalvikInst.registerCount) {
            when (i) {
                0 -> {}
                1 -> visitLoad(dalvikInst.a, frame)
                2 -> visitLoad(dalvikInst.b, frame)
                3 -> visitLoad(dalvikInst.c, frame)
                4 -> visitLoad(dalvikInst.d, frame)
                5 -> visitLoad(dalvikInst.e, frame)
                else -> throw DecodeException("invoke instruction register number error.")
            }
        }
        mthVisit.visitMethodInsn(invokeType, mthInfo.declClass.className(), mthInfo.name, mthInfo.parseSignature(), false)
        return SlotType.convert(mthInfo.retType)
    }

    private fun visitLabel(label0: Label?, lineNumber: Int?) {
        if (label0 == null) return
        mthVisit.visitLabel(label0)
        if (lineNumber != null) mthVisit.visitLineNumber(lineNumber, label0)
    }

    private fun visitConst(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame) {
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
        val slot = slotNum(dalvikInst.a)
        mthVisit.visitVarInsn(jvmOpcodes.ISTORE, slot)
        frame.setSlot(slot, SlotType.INT)
    }

    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame) {
        visitLoad(dalvikInst.a, frame)
        visitLoad(dalvikInst.b, frame)
        val target = code(dalvikInst.target)?.getLabel()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable")
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQ + jvmOpcodes.IF_ICMPEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(dalvikInst.target)
    }

    private fun visitIfStmt(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame) {
        visitLoad(dalvikInst.a, frame)
        val target = code(dalvikInst.target)?.getLabel()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable")
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQZ + jvmOpcodes.IFEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(dalvikInst.target)
    }

    private fun visitLoad(slot: Int, frame: StackFrame): SlotType {
        val slotType = frame.getSlot(slot) ?: throw DecodeException("Empty slot $slot")
        when (slotType) {
            in SlotType.BYTE..SlotType.INT -> {
                mthVisit.visitVarInsn(jvmOpcodes.ILOAD, slot)
            }
            SlotType.LONG -> {
                mthVisit.visitVarInsn(jvmOpcodes.LLOAD, slot)
            }
            SlotType.FLOAT -> {
                mthVisit.visitVarInsn(jvmOpcodes.FLOAD, slot)
            }
            SlotType.DOUBLE -> {
                mthVisit.visitVarInsn(jvmOpcodes.DLOAD, slot)
            }
            SlotType.OBJECT -> {
                mthVisit.visitVarInsn(jvmOpcodes.ALOAD, slot)
            }
            else -> {
                // TODO
            }
        }
        return slotType
    }

    private fun visitStore(type: SlotType, slot: Int, frame: StackFrame) {
        when (type) {
            in SlotType.BYTE..SlotType.INT -> {
                mthVisit.visitVarInsn(jvmOpcodes.ISTORE, slot)
                frame.setSlot(slot, type)
            }
            SlotType.LONG -> {
                mthVisit.visitVarInsn(jvmOpcodes.LSTORE, slot)
                frame.setSlotWide(slot, type)
            }
            SlotType.FLOAT -> {
                mthVisit.visitVarInsn(jvmOpcodes.FSTORE, slot)
                frame.setSlot(slot, type)
            }
            SlotType.DOUBLE -> {
                mthVisit.visitVarInsn(jvmOpcodes.DSTORE, slot)
                frame.setSlotWide(slot, type)
            }
            SlotType.OBJECT -> {
                mthVisit.visitVarInsn(jvmOpcodes.ASTORE, slot)
                frame.setSlot(slot, type)
            }
            else -> {
                // TODO
            }
        }
    }

    private fun visitReturnVoid() {
        mthVisit.visitInsn(jvmOpcodes.RETURN)
    }

    private fun visitReturn(slot: Int, frame: StackFrame) {
        val type = visitLoad(slot, frame)
        when (type) {
            in SlotType.BYTE..SlotType.INT -> {
                mthVisit.visitInsn(jvmOpcodes.IRETURN)
            }
            SlotType.LONG -> {
                mthVisit.visitInsn(jvmOpcodes.LRETURN)
            }
            SlotType.FLOAT -> {
                mthVisit.visitInsn(jvmOpcodes.FRETURN)
            }
            SlotType.DOUBLE -> {
                mthVisit.visitInsn(jvmOpcodes.DRETURN)
            }
            SlotType.OBJECT -> {
                mthVisit.visitInsn(jvmOpcodes.ARETURN)
            }
            else -> {

            }
        }
    }

    private fun visitGoto(dalvikInst: ZeroRegisterDecodedInstruction) {
        val target = code(dalvikInst.target)?.getLabel()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable")
        mthVisit.visitJumpInsn(jvmOpcodes.GOTO, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(dalvikInst.target)
    }
}