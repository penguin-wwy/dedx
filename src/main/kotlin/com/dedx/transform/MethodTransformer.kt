package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.android.dx.io.instructions.OneRegisterDecodedInstruction
import com.android.dx.io.instructions.TwoRegisterDecodedInstruction
import com.android.dx.io.instructions.ZeroRegisterDecodedInstruction
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.pass.DataFlowAnalysisPass
import com.dedx.dex.pass.DataFlowMethodInfo
import com.dedx.dex.struct.*
import com.dedx.tools.Configuration
import com.dedx.utils.DecodeException
import com.dedx.utils.TypeConfliction
import org.objectweb.asm.Label
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
    var newestReturn: SlotType? = null

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
                entryFrame.setSlot(slotNum(type.regNum), SlotType.convert(type.type)!!)
            }

            visitTryCatchBlock()
            var prevLineNumber = 0
            for (inst in mthNode.codeList) {
                if (inst != null) normalProcess(inst, prevLineNumber)
            }
            mthVisit.visitEnd()
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
        val offset = mthNode.regsCount - mthNode.ins
        if (regNum < offset) {
            return regNum + mthNode.ins
        } else {
            return regNum - offset
        }
    }

    fun DecodedInstruction.regA() = slotNum(a)
    fun DecodedInstruction.regB() = slotNum(b)
    fun DecodedInstruction.regC() = slotNum(c)
    fun DecodedInstruction.regD() = slotNum(d)
    fun DecodedInstruction.regE() = slotNum(e)

    private fun normalProcess(inst: InstNode, prevLineNumber: Int) {
        if (inst.getLineNumber() != prevLineNumber) {
            visitLabel(inst.getLableOrPut().value, inst.getLineNumber())
        }
        val frame = StackFrame.getFrameOrPut(inst.cursor).merge()
        val dalvikInst = inst.instruction
        // mark last time invoke-kind's return result
        when (dalvikInst.opcode) {
            in Opcodes.MOVE..Opcodes.MOVE_OBJECT_16 -> {
                visitMove(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            }
            in Opcodes.MOVE_RESULT..Opcodes.MOVE_RESULT_OBJECT -> {
                visitStore(newestReturn ?: throw DecodeException("MOVE_RESULT by null", inst.cursor), dalvikInst.regA(), frame)
            }
            Opcodes.MOVE_EXCEPTION -> {
                visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
            }
            Opcodes.RETURN_VOID -> {
                visitReturnVoid()
            }
            in Opcodes.RETURN..Opcodes.RETURN_OBJECT -> {
                visitReturn(dalvikInst.regA(), frame, inst.cursor)
            }
            in Opcodes.CONST_4..Opcodes.CONST_CLASS -> {
                visitConst(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.MONITOR_ENTER -> {

            }
            Opcodes.MONITOR_EXIT -> {

            }
            Opcodes.CHECK_CAST -> {

            }
            Opcodes.INSTANCE_OF -> {

            }
            Opcodes.ARRAY_LENGTH -> {

            }
            Opcodes.NEW_INSTANCE -> {

            }
            Opcodes.NEW_ARRAY -> {

            }
            Opcodes.FILLED_NEW_ARRAY -> {

            }
            Opcodes.FILLED_NEW_ARRAY_RANGE -> {

            }
            Opcodes.FILL_ARRAY_DATA -> {

            }
            Opcodes.THROW -> visitThrow(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.GOTO..Opcodes.GOTO_32 -> visitGoto(dalvikInst as ZeroRegisterDecodedInstruction, inst.cursor)
            Opcodes.PACKED_SWITCH -> {

            }
            Opcodes.SPARSE_SWITCH -> {

            }
            in Opcodes.CMPL_FLOAT..Opcodes.CMP_LONG -> {

            }
            in Opcodes.IF_EQ..Opcodes.IF_LE -> visitIfStmt(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.IF_EQZ..Opcodes.IF_LEZ -> visitIfStmt(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.AGET..Opcodes.AGET_SHORT -> {

            }
            in Opcodes.APUT..Opcodes.APUT_SHORT -> {

            }
            in Opcodes.IGET..Opcodes.IGET_SHORT -> {}
            in Opcodes.IPUT..Opcodes.IPUT_SHORT -> {}
            in Opcodes.SGET..Opcodes.SGET_SHORT -> visitGetField(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.SPUT..Opcodes.SPUT_SHORT -> {}
            Opcodes.INVOKE_VIRTUAL -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKEVIRTUAL, frame, inst.cursor)
            Opcodes.INVOKE_SUPER -> {
                // TODO
            }
            Opcodes.INVOKE_DIRECT -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESPECIAL, frame, inst.cursor)
            Opcodes.INVOKE_STATIC -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESTATIC, frame, inst.cursor)
            Opcodes.INVOKE_INTERFACE -> {
                // TODO
            }
            Opcodes.INVOKE_VIRTUAL_RANGE -> {}
            Opcodes.INVOKE_SUPER_RANGE -> {}
            Opcodes.INVOKE_DIRECT_RANGE -> {}
            Opcodes.INVOKE_STATIC_RANGE -> {}
            Opcodes.INVOKE_INTERFACE_RANGE -> {}
            in Opcodes.NEG_INT..Opcodes.INT_TO_SHORT -> {}
            in Opcodes.ADD_INT..Opcodes.REM_DOUBLE -> {}
            in Opcodes.ADD_INT_2ADDR..Opcodes.USHR_INT_2ADDR,
            in Opcodes.ADD_FLOAT_2ADDR..Opcodes.REM_FLOAT_2ADDR -> {
                visitBinOp2Addr(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            }
            in Opcodes.ADD_LONG_2ADDR..Opcodes.USHR_LONG_2ADDR,
            in Opcodes.ADD_DOUBLE_2ADDR..Opcodes.REM_DOUBLE_2ADDR -> visitBinOpWide2Addr(
                    dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.ADD_INT_LIT16..Opcodes.USHR_INT_LIT8 -> visitBinOp(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
        }
    }

    private fun visitInvoke(dalvikInst: DecodedInstruction, invokeType: Int, frame: StackFrame, offset: Int): SlotType? {
        val mthInfo = MethodInfo.fromDex(dexNode, dalvikInst.index)
        for (i in 0..dalvikInst.registerCount) {
            when (i) {
                0 -> {}
                1 -> visitLoad(dalvikInst.regA(), frame, offset)
                2 -> visitLoad(dalvikInst.regB(), frame, offset)
                3 -> visitLoad(dalvikInst.regC(), frame, offset)
                4 -> visitLoad(dalvikInst.regD(), frame, offset)
                5 -> visitLoad(dalvikInst.regE(), frame, offset)
                else -> throw DecodeException("invoke instruction register number error.", offset)
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

    private fun visitConst(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        var type: SlotType? = null
        when (dalvikInst.opcode) {
            Opcodes.CONST_4 -> {
                mthVisit.visitIntInsn(jvmOpcodes.BIPUSH, dalvikInst.literalByte)
                type = SlotType.INT
            }
            in Opcodes.CONST_16..Opcodes.CONST_HIGH16 -> {
                mthVisit.visitIntInsn(jvmOpcodes.SIPUSH, dalvikInst.literalInt)
                type = SlotType.INT
            }
            in Opcodes.CONST_WIDE_16..Opcodes.CONST_WIDE_HIGH16 -> {
                // TODO long push
                type = SlotType.LONG
            }
            Opcodes.CONST_STRING, Opcodes.CONST_STRING_JUMBO -> {
                type = SlotType.INT.setConstantPoolAttr(dalvikInst.index)
            }
            Opcodes.CONST_CLASS -> {}
             else -> {
                 throw DecodeException("decode in visitConst", offset)
            }
        }
        val slot = dalvikInst.regA()
        if (type != null) visitStore(type, slot, frame)
    }

    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), frame, offset)
        visitLoad(dalvikInst.regB(), frame, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQ + jvmOpcodes.IF_ICMPEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitIfStmt(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), frame, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQZ + jvmOpcodes.IFEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitLoad(slot: Int, frame: StackFrame, offset: Int): SlotType {
        val slotType = frame.getSlot(slot) ?: throw DecodeException("Empty slot $slot", offset)
        when (slotType) {
            in SlotType.BYTE..SlotType.INT -> {
                if (slotType.isConstantPoolIndex()) {
                    mthVisit.visitLdcInsn(dexNode.getString(slotType.getConstantPoolAttr()))
                } else {
                    mthVisit.visitVarInsn(jvmOpcodes.ILOAD, slot)
                }
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
                if (!type.isConstantPoolIndex()) {
                    mthVisit.visitVarInsn(jvmOpcodes.ISTORE, slot)
                }
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

    private fun visitReturn(slot: Int, frame: StackFrame, offset: Int) {
        val type = visitLoad(slot, frame, offset)
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

    private fun visitGoto(dalvikInst: ZeroRegisterDecodedInstruction, offset: Int) {
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        mthVisit.visitJumpInsn(jvmOpcodes.GOTO, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitGetField(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val fieldInfo = FieldInfo.fromDex(dexNode, dalvikInst.index)
        if (fieldInfo.declClass != mthNode.parent.clsInfo) { // determine this field in parent class
            mthVisit.visitFieldInsn(jvmOpcodes.GETSTATIC, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
        } else {
            val fieldNode = mthNode.parent.searchField(fieldInfo) ?: throw DecodeException("Get field $fieldInfo failed.", offset)
            if (fieldNode.isStatic()) {
                mthVisit.visitFieldInsn(jvmOpcodes.GETSTATIC, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
            } else {
                mthVisit.visitFieldInsn(jvmOpcodes.GETFIELD, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
            }
        }
        visitStore(SlotType.convert(fieldInfo.type)!!, dalvikInst.regA(), frame)
    }

    private fun visitBinOp2Addr(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            var type = SlotType.INT
            if (dalvikInst.opcode >= Opcodes.ADD_FLOAT_2ADDR) {
                type = SlotType.FLOAT
            }
            val regA = dalvikInst.regA()
            val regB = dalvikInst.regB()
            StackFrame.checkType(type, offset, regA, regB)
            visitLoad(regA, frame, offset)
            visitLoad(regB, frame, offset)
            when (dalvikInst.opcode) {
                Opcodes.ADD_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IADD)
                Opcodes.SUB_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.ISUB)
                Opcodes.MUL_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IMUL)
                Opcodes.DIV_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IDIV)
                Opcodes.REM_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IREM)
                Opcodes.AND_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IAND)
                Opcodes.OR_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IOR)
                Opcodes.XOR_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IXOR)
                Opcodes.SHL_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.ISHL)
                Opcodes.SHR_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.ISHR)
                Opcodes.USHR_INT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.IUSHR)

                Opcodes.ADD_FLOAT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.FADD)
                Opcodes.SUB_FLOAT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.FSUB)
                Opcodes.MUL_FLOAT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.FMUL)
                Opcodes.DIV_FLOAT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.FDIV)
                Opcodes.REM_FLOAT_2ADDR -> mthVisit.visitInsn(jvmOpcodes.FREM)
            }
            visitStore(type, regA, frame)
        } catch (ex: Exception) {
            when (ex) {
                is DecodeException, is TypeConfliction -> {
                    throw DecodeException("BinOp2Addr error", offset, ex)
                }
                else -> throw ex
            }
        }
    }

    private fun visitBinOpWide2Addr(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {

    }

    private fun visitBinOp(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            val regA = dalvikInst.regA()
            val regB = dalvikInst.regB()
            StackFrame.checkType(SlotType.INT, offset, regB)
            if (dalvikInst.opcode == Opcodes.RSUB_INT || dalvikInst.opcode == Opcodes.RSUB_INT_LIT8) {
                mthVisit.visitIntInsn(jvmOpcodes.SIPUSH, dalvikInst.literalInt)
                visitLoad(regB, frame, offset)
            } else {
                visitLoad(regB, frame, offset)
                mthVisit.visitIntInsn(jvmOpcodes.SIPUSH, dalvikInst.literalInt)
            }
            when (dalvikInst.opcode) {
                Opcodes.ADD_INT_LIT8, Opcodes.ADD_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IADD)
                Opcodes.RSUB_INT_LIT8, Opcodes.RSUB_INT -> mthVisit.visitInsn(jvmOpcodes.ISUB)
                Opcodes.MUL_INT_LIT8, Opcodes.MUL_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IMUL)
                Opcodes.DIV_INT_LIT8, Opcodes.DIV_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IDIV)
                Opcodes.REM_INT_LIT8, Opcodes.REM_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IREM)
                Opcodes.AND_INT_LIT8, Opcodes.AND_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IAND)
                Opcodes.OR_INT_LIT8, Opcodes.OR_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IOR)
                Opcodes.XOR_INT_LIT8, Opcodes.XOR_INT_LIT16 -> mthVisit.visitInsn(jvmOpcodes.IXOR)
                Opcodes.SHL_INT_LIT8 -> mthVisit.visitInsn(jvmOpcodes.ISHL)
                Opcodes.SHR_INT_LIT8 -> mthVisit.visitInsn(jvmOpcodes.ISHR)
                Opcodes.USHR_INT_LIT8 -> mthVisit.visitInsn(jvmOpcodes.IUSHR)
            }
            visitStore(SlotType.INT, regA, frame)
        } catch (ex: Exception) {
            when (ex) {
                is DecodeException, is TypeConfliction -> {
                    throw DecodeException("BinOp error", offset, ex)
                }
                else -> throw ex
            }
        }
    }

    private fun visitMove(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitStore(visitLoad(dalvikInst.regB(), frame, offset), dalvikInst.regA(), frame)
    }

    private fun visitThrow(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            StackFrame.checkType(SlotType.OBJECT, offset, dalvikInst.regA())
            visitLoad(dalvikInst.regA(), frame, offset)
            mthVisit.visitInsn(jvmOpcodes.ATHROW)
        } catch (ex: Exception) {
            when (ex) {
                is DecodeException, is TypeConfliction -> {
                    throw DecodeException("Throw error", offset, ex)
                }
                else -> throw ex
            }
        }
    }
}