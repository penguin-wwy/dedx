package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.*
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
    var prevLineNumber: Int = 0

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

            SlotType.initConstantValue()
            StackFrame.initInstFrame(mthNode)
            val entryFrame = StackFrame.getFrameOrPut(0)
            for (type in mthNode.argsList) {
                entryFrame.setSlot(slotNum(type.regNum), SlotType.convert(type.type)!!)
            }

            visitTryCatchBlock()
            prevLineNumber = 0
            for (inst in mthNode.codeList) {
                if (inst != null) normalProcess(inst)
            }
            mthVisit.visitMaxs(mthNode.regsCount, mthNode.regsCount)
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

    private fun normalProcess(inst: InstNode) {
        if (inst.getLineNumber() != prevLineNumber) {
            this.prevLineNumber = inst.getLineNumber()!!
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
                visitReturn(dalvikInst.regA(), SlotType.convert(mthNode.getReturnType())!!, inst.cursor)
            }
            in Opcodes.CONST_4..Opcodes.CONST_CLASS -> {
                visitConst(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.MONITOR_ENTER -> {
                visitMonitor(true, dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.MONITOR_EXIT -> {
                visitMonitor(false, dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
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
            Opcodes.THROW -> visitThrow(dalvikInst as OneRegisterDecodedInstruction, inst.cursor)
            in Opcodes.GOTO..Opcodes.GOTO_32 -> visitGoto(dalvikInst as ZeroRegisterDecodedInstruction, inst.cursor)
            Opcodes.PACKED_SWITCH -> {

            }
            Opcodes.SPARSE_SWITCH -> {

            }
            in Opcodes.CMPL_FLOAT..Opcodes.CMP_LONG -> visitCmpStmt(dalvikInst as ThreeRegisterDecodedInstruction, frame, inst.cursor)
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
        val thisPos = when (invokeType) {
            InvokeType.INVOKESTATIC -> 0
            else -> 1
        }
        if (dalvikInst.registerCount != mthInfo.args.size + thisPos)
            throw DecodeException("argument count error", offset)
        if (thisPos == 1) {
            for (i in 0 until dalvikInst.registerCount) {
                val argPos = i - thisPos
                when (argPos) {
                    -1 -> visitLoad(dalvikInst.regA(), SlotType.OBJECT, offset) // push this
                    0 -> visitLoad(dalvikInst.regB(), SlotType.convert(mthInfo.args[argPos])!!, offset)
                    1 -> visitLoad(dalvikInst.regC(), SlotType.convert(mthInfo.args[argPos])!!, offset)
                    2 -> visitLoad(dalvikInst.regD(), SlotType.convert(mthInfo.args[argPos])!!, offset)
                    3 -> visitLoad(dalvikInst.regE(), SlotType.convert(mthInfo.args[argPos])!!, offset)
                    else -> throw DecodeException("invoke instruction register number error.", offset)
                }
            }
        } else {
            for(i in 0 until dalvikInst.registerCount) {
                when (i) {
                    0 -> visitLoad(dalvikInst.regA(), SlotType.convert(mthInfo.args[i])!!, offset)
                    1 -> visitLoad(dalvikInst.regB(), SlotType.convert(mthInfo.args[i])!!, offset)
                    2 -> visitLoad(dalvikInst.regC(), SlotType.convert(mthInfo.args[i])!!, offset)
                    3 -> visitLoad(dalvikInst.regD(), SlotType.convert(mthInfo.args[i])!!, offset)
                    4 -> visitLoad(dalvikInst.regE(), SlotType.convert(mthInfo.args[i])!!, offset)
                    else -> throw DecodeException("invoke instruction register number error.", offset)
                }
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
        val slot = dalvikInst.regA()
        when (dalvikInst.opcode) {
            Opcodes.CONST_4 -> {
                frame.setSlot(slot, SlotType.BYTE)
                SlotType.setLiteral(slot, false, dalvikInst.literalByte.toLong())
            }
            Opcodes.CONST_16 -> {
                frame.setSlot(slot, SlotType.SHORT)
                SlotType.setLiteral(slot, false, dalvikInst.literalUnit.toLong())
            }
            in Opcodes.CONST..Opcodes.CONST_HIGH16 -> {
                frame.setSlot(slot, SlotType.INT)
                SlotType.setLiteral(slot, false, dalvikInst.literalInt.toLong())
            }
            in Opcodes.CONST_WIDE_16..Opcodes.CONST_WIDE_HIGH16 -> {
                frame.setSlot(slot, SlotType.LONG) // also double type
                SlotType.setLiteral(slot, false, dalvikInst.literal)
            }
            Opcodes.CONST_STRING, Opcodes.CONST_STRING_JUMBO, Opcodes.CONST_CLASS -> {
                frame.setSlot(slot, SlotType.INT) // constant pool index as int type
                SlotType.setLiteral(slot, true, dalvikInst.index.toLong())
            }
        }
    }

    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        visitLoad(dalvikInst.regB(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQ + jvmOpcodes.IF_ICMPEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitIfStmt(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        mthVisit.visitJumpInsn(dalvikInst.opcode - Opcodes.IF_EQZ + jvmOpcodes.IFEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitLoad(slot: Int, slotType: SlotType, offset: Int): SlotType {
        if (SlotType.isConstant(slot)) {
            visitPushOrLdc(slot, slotType, offset)
            return slotType
        }
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

    private fun SlotType.Companion.isConstant(slot: Int)
            = SlotType.isConstantPoolIndex(slot) || SlotType.isConstantPoolLiteral(slot)

    private fun visitPushOrLdc(slot: Int, slotType: SlotType, offset: Int) {
        try {
            val literal = SlotType.getLiteral(slot)
            if (SlotType.isConstantPoolLiteral(slot)) {
                visitPushOrLdc(false, literal, slotType, offset)
            }
            if (SlotType.isConstantPoolIndex(slot)) {
                visitPushOrLdc(true, literal, slotType, offset)
            }
        } catch (de: DecodeException) {
            throw DecodeException("LDC instruction error", offset, de)
        }
    }

    private fun visitPushOrLdc(isIndex: Boolean, literal: Long, slotType: SlotType, offset: Int) {
        if (isIndex) {
            mthVisit.visitLdcInsn(dexNode.getString(literal.toInt()))
        } else {
            when (slotType) {
                SlotType.BYTE -> mthVisit.visitIntInsn(jvmOpcodes.BIPUSH, literal.toInt())
                SlotType.SHORT -> mthVisit.visitIntInsn(jvmOpcodes.SIPUSH, literal.toInt())
                SlotType.INT -> {
                    val intLiteral = literal.toInt()
                    if (intLiteral in -1..5) {
                        mthVisit.visitInsn(jvmOpcodes.ICONST_M1 + intLiteral + 1)
                    } else {
                        mthVisit.visitLdcInsn(literal.toInt())
                    }
                }
                SlotType.FLOAT -> {
                    val floatBits = literal.toInt()
                    when (floatBits) {
                        0.0f.toRawBits() -> mthVisit.visitInsn(jvmOpcodes.FCONST_0)
                        1.0f.toRawBits() -> mthVisit.visitInsn(jvmOpcodes.FCONST_1)
                        2.0f.toRawBits() -> mthVisit.visitInsn(jvmOpcodes.FCONST_2)
                        else -> mthVisit.visitLdcInsn(Float.fromBits(floatBits))
                    }
                }
                SlotType.LONG -> {
                    when (literal) {
                        0L -> mthVisit.visitInsn(jvmOpcodes.LCONST_0)
                        1L -> mthVisit.visitInsn(jvmOpcodes.LCONST_1)
                        else -> mthVisit.visitLdcInsn(literal)
                    }
                }
                SlotType.DOUBLE -> {
                    when (literal) {
                        0.0.toRawBits() -> mthVisit.visitInsn(jvmOpcodes.DCONST_0)
                        1.0.toRawBits() -> mthVisit.visitInsn(jvmOpcodes.DCONST_1)
                        else -> mthVisit.visitLdcInsn(Double.fromBits(literal))
                    }
                }
                else -> {
                    throw DecodeException("Const type error", offset)
                }
            }
        }
    }

    private fun visitStore(type: SlotType, slot: Int, frame: StackFrame) {
        SlotType.delLiteral(slot)
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

    private fun visitReturn(slot: Int, type: SlotType, offset: Int) {
        visitLoad(slot, type, offset)
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
        val targetCode = code(dalvikInst.target) ?: throw DecodeException("Goto target [${dalvikInst.target}] is error", offset)
        if (targetCode.instruction.opcode in Opcodes.RETURN..Opcodes.RETURN_OBJECT) {
            visitReturn(targetCode.instruction.regA(), SlotType.convert(mthNode.getReturnType())!!, offset)
        } else {
            val target = targetCode.getLableOrPut().value
            mthVisit.visitJumpInsn(jvmOpcodes.GOTO, target)
            StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
        }

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
            visitLoad(regA, type, offset)
            visitLoad(regB, type, offset)
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
            if (!SlotType.isConstant(regB)) {
                StackFrame.checkType(SlotType.INT, offset, regB)
            } // because of missing type information for constants, can't check type for constants
            if (dalvikInst.opcode == Opcodes.RSUB_INT || dalvikInst.opcode == Opcodes.RSUB_INT_LIT8) {
                visitPushOrLdc(false, dalvikInst.literalInt.toLong(), SlotType.INT, offset)
                visitLoad(regB, SlotType.SHORT, offset)
            } else {
                visitLoad(regB, SlotType.INT, offset)
                visitPushOrLdc(false, dalvikInst.literalInt.toLong(), SlotType.INT, offset)
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
        val regB = dalvikInst.regB()
        val slotType = frame.slot2type[regB] ?: throw DecodeException("Empty type in slot [$regB]")
        visitStore(visitLoad(regB, slotType, offset), dalvikInst.regA(), frame)
    }

    private fun visitThrow(dalvikInst: OneRegisterDecodedInstruction, offset: Int) {
        try {
            val regA = dalvikInst.regA()
            StackFrame.checkType(SlotType.OBJECT, offset, regA)
            visitLoad(regA, SlotType.OBJECT, offset)
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

    private fun visitCmpStmt(dalvikInst: ThreeRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            val regA = dalvikInst.regA()
            val regB = dalvikInst.regB()
            val regC = dalvikInst.regC()
            when (dalvikInst.opcode) {
                Opcodes.CMPL_FLOAT, Opcodes.CMPG_FLOAT -> {
                    visitLoad(regB, SlotType.FLOAT, offset)
                    visitLoad(regC, SlotType.FLOAT, offset)
                    mthVisit.visitInsn(jvmOpcodes.FCMPL + dalvikInst.opcode - Opcodes.CMPL_FLOAT)
                }
                Opcodes.CMPL_DOUBLE, Opcodes.CMPG_DOUBLE -> {
                    visitLoad(regB, SlotType.DOUBLE, offset)
                    visitLoad(regC, SlotType.DOUBLE, offset)
                    mthVisit.visitInsn(jvmOpcodes.DCMPL + dalvikInst.opcode - Opcodes.CMPL_DOUBLE)
                }
                Opcodes.CMP_LONG -> {
                    visitLoad(regB, SlotType.LONG, offset)
                    visitLoad(regC, SlotType.LONG, offset)
                    mthVisit.visitInsn(jvmOpcodes.LCMP)
                }
            }
            visitStore(SlotType.INT, regA, frame)
        } catch (ex: Exception) {

        }
    }

    private fun visitMonitor(isEntry: Boolean, dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            val regA = dalvikInst.regA()
            StackFrame.checkType(SlotType.OBJECT, offset, regA)
            visitLoad(regA, SlotType.OBJECT, offset)
            if (isEntry) {
                mthVisit.visitInsn(jvmOpcodes.MONITORENTER)
            } else {
                mthVisit.visitInsn(jvmOpcodes.MONITOREXIT)
            }
        } catch (ex: Exception) {

        }
    }
}