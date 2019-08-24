package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.*
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.pass.DataFlowAnalysisPass
import com.dedx.dex.pass.DataFlowMethodInfo
import com.dedx.dex.struct.*
import com.dedx.dex.struct.type.BasicType
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

    val jvmInstManager = InstTransformer(this)
    val blockMap = HashMap<Label, BasicBlock>()
    val inst2Block = HashMap<InstNode, BasicBlock>()
    var currBlock: BasicBlock? = null
    val dexNode = mthNode.dex()
    var ropper = ReRopper(mthNode.codeSize)
    var entry: BasicBlock? = null
    var dfInfo: DataFlowMethodInfo? = null
    var exits = ArrayList<BasicBlock>()
    var newestReturn: SlotType? = null // mark last time invoke-kind's return result
    var prevLineNumber: Int = 0 // mark last time line number
    var jvmLabel: Label? = null
    var jvmLine: Int? = null
    var skipInst = 0 // mark instruction number which skip

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
        if (Configuration.optLevel >= Configuration.Optimized) {
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
            skipInst = 0
            // add JvmInst to manager
            for (inst in mthNode.codeList) {
                if (inst == null) continue
                if (skipInst > 0) {
                    skipInst--
                    continue
                }
                normalProcess(inst)
            }
            jvmInstManager.visitJvmInst()
            // TODO: Calculate the number of slots and stack depth
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

    private fun slotNum(regNum: Int): Int {
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

    private fun getStartJvmLabel(): Label? {
        if (jvmLabel != null) {
            val label0: Label = jvmLabel!!
            jvmLabel = null
            return label0
        }
        return null
    }

    private fun getStartJvmLine(): Int? {
        if (jvmLine != null) {
            val line: Int = jvmLine!!
            jvmLine = null
            return line
        }
        return null
    }

    private fun pushSingleInst(opcodes: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateSingleInst(opcodes, getStartJvmLabel(), getStartJvmLine()))
    private fun pushSlotInst(opcodes: Int, slot: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateSlotInst(opcodes, slot, getStartJvmLabel(), getStartJvmLine()))
    private fun pushIntInst(opcodes: Int, number: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateIntInst(opcodes, number, getStartJvmLabel(), getStartJvmLine()))
    private fun pushLiteralInst(opcodes: Int, literal: Long, type: SlotType)
            = jvmInstManager.pushJvmInst(JvmInst.CreateLiteralInst(opcodes, literal, type, getStartJvmLabel(), getStartJvmLine()))
    private fun pushTypeInst(opcodes: Int, type: String)
            = jvmInstManager.pushJvmInst(JvmInst.CreateTypeInst(opcodes, type, getStartJvmLabel(), getStartJvmLine()))
    private fun pushConstantInst(opcodes: Int, constIndex: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateConstantInst(opcodes, constIndex, getStartJvmLabel(), getStartJvmLine()))
    private fun pushInvokeInst(invokeType: Int, mthIndex: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateInvokeInst(invokeType, invokeType, mthIndex, getStartJvmLabel(), getStartJvmLine()))
    private fun pushJumpInst(opcodes: Int, target: Label)
            = jvmInstManager.pushJvmInst(JvmInst.CreateJumpInst(opcodes, target, getStartJvmLabel(), getStartJvmLine()))
    private fun pushFieldInst(opcodes: Int, fieldIndex: Int)
            = jvmInstManager.pushJvmInst(JvmInst.CreateFieldInst(opcodes, fieldIndex, getStartJvmLabel(), getStartJvmLine()))

    private fun DecodedInstruction.regA() = slotNum(a)
    private fun DecodedInstruction.regB() = slotNum(b)
    private fun DecodedInstruction.regC() = slotNum(c)
    private fun DecodedInstruction.regD() = slotNum(d)
    private fun DecodedInstruction.regE() = slotNum(e)

    private fun normalProcess(inst: InstNode) {

        /*should use a safer way of assigning*/
        jvmLabel = inst.getLableOrPut().value
        jvmLine = inst.getLineNumber()

        val frame = StackFrame.getFrameOrPut(inst.cursor).merge()
        val dalvikInst = inst.instruction
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
                visitCheckCast(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.INSTANCE_OF -> {
                visitInstanceOf(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.ARRAY_LENGTH -> {
                visitArrayLength(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.NEW_INSTANCE -> {
                visitNewInstance(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.NEW_ARRAY -> {
                visitNewArray(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
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
                visitArrayOp(dalvikInst as ThreeRegisterDecodedInstruction, frame, inst.cursor)
            }
            in Opcodes.APUT..Opcodes.APUT_SHORT -> {
                visitArrayOp(dalvikInst as ThreeRegisterDecodedInstruction, frame, inst.cursor)
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
        pushInvokeInst(invokeType, dalvikInst.index)
        return SlotType.convert(mthInfo.retType)
    }

    private fun visitConst(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val slot = dalvikInst.regA()
        when (dalvikInst.opcode) {
            Opcodes.CONST_4 -> {
                frame.setSlot(slot, SlotType.BYTE)
                SlotType.setLiteral(slot, dalvikInst.literalByte.toLong(), SlotType.isValue)
            }
            Opcodes.CONST_16 -> {
                frame.setSlot(slot, SlotType.SHORT)
                SlotType.setLiteral(slot, dalvikInst.literalUnit.toLong(), SlotType.isValue)
            }
            in Opcodes.CONST..Opcodes.CONST_HIGH16 -> {
                frame.setSlot(slot, SlotType.INT)
                SlotType.setLiteral(slot, dalvikInst.literalInt.toLong(), SlotType.isValue)
            }
            in Opcodes.CONST_WIDE_16..Opcodes.CONST_WIDE_HIGH16 -> {
                frame.setSlot(slot, SlotType.LONG) // also double type
                SlotType.setLiteral(slot, dalvikInst.literal, SlotType.isValue)
            }
            Opcodes.CONST_STRING, Opcodes.CONST_STRING_JUMBO -> {
                frame.setSlot(slot, SlotType.INT) // constant pool index as int type
                SlotType.setLiteral(slot, dalvikInst.index.toLong(), SlotType.isStrIndex)
            }
            Opcodes.CONST_CLASS -> {
                frame.setSlot(slot, SlotType.INT) // constant pool index as int type
                SlotType.setLiteral(slot, dalvikInst.index.toLong(), SlotType.isTypeIndex)
            }
        }
    }

    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        visitLoad(dalvikInst.regB(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        pushJumpInst(dalvikInst.opcode - Opcodes.IF_EQ + jvmOpcodes.IF_ICMPEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitIfStmt(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLableOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        pushJumpInst(dalvikInst.opcode - Opcodes.IF_EQZ + jvmOpcodes.IFEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitLoad(slot: Int, slotType: SlotType, offset: Int): SlotType {
        if (SlotType.isConstant(slot)) {
            visitPushOrLdc(slot, slotType, offset)
            return slotType
        }
        when (slotType) {
            in SlotType.BYTE..SlotType.INT -> {
                pushSlotInst(jvmOpcodes.ILOAD, slot)
            }
            SlotType.LONG -> {
                pushSlotInst(jvmOpcodes.LLOAD, slot)
            }
            SlotType.FLOAT -> {
                pushSlotInst(jvmOpcodes.FLOAD, slot)
            }
            SlotType.DOUBLE -> {
                pushSlotInst(jvmOpcodes.DLOAD, slot)
            }
            SlotType.OBJECT, SlotType.ARRAY -> {
                pushSlotInst(jvmOpcodes.ALOAD, slot)
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
                visitPushOrLdc(literal, slotType, offset)
            }
            if (SlotType.isStringIndex(slot)) {
                pushConstantInst(jvmOpcodes.LDC, literal.toInt())
            }
            if (SlotType.isTypeIndex(slot)) {
                pushTypeInst(jvmOpcodes.LDC, dexNode.getType(literal.toInt()).descriptor())
            }
        } catch (de: DecodeException) {
            throw DecodeException("LDC instruction error", offset, de)
        }
    }

    private fun visitPushOrLdc(literal: Long, slotType: SlotType, offset: Int) {
        when (slotType) {
            SlotType.BYTE -> pushIntInst(jvmOpcodes.BIPUSH, literal.toInt())
            SlotType.SHORT -> pushIntInst(jvmOpcodes.SIPUSH, literal.toInt())
            SlotType.INT -> {
                val intLiteral = literal.toInt()
                if (intLiteral in -1..5) {
                    pushSingleInst(jvmOpcodes.ICONST_M1 + intLiteral + 1)
                } else {
                    pushLiteralInst(jvmOpcodes.LDC, literal, SlotType.INT)
                }
            }
            SlotType.FLOAT -> {
                val floatBits = literal.toInt()
                when (floatBits) {
                    0.0f.toRawBits() -> pushSingleInst(jvmOpcodes.FCONST_0)
                    1.0f.toRawBits() -> pushSingleInst(jvmOpcodes.FCONST_1)
                    2.0f.toRawBits() -> pushSingleInst(jvmOpcodes.FCONST_2)
                    else -> pushLiteralInst(jvmOpcodes.LDC, literal, SlotType.FLOAT)
                }
            }
            SlotType.LONG -> {
                when (literal) {
                    0L -> pushSingleInst(jvmOpcodes.LCONST_0)
                    1L -> pushSingleInst(jvmOpcodes.LCONST_1)
                    else -> pushLiteralInst(jvmOpcodes.LDC, literal, SlotType.LONG)
                }
            }
            SlotType.DOUBLE -> {
                when (literal) {
                    0.0.toRawBits() -> pushSingleInst(jvmOpcodes.DCONST_0)
                    1.0.toRawBits() -> pushSingleInst(jvmOpcodes.DCONST_1)
                    else -> pushLiteralInst(jvmOpcodes.LDC, literal, SlotType.DOUBLE)
                }
            }
            else -> {
                throw DecodeException("Const type error", offset)
            }
        }
    }

    private fun visitStore(type: SlotType, slot: Int, frame: StackFrame) {
        SlotType.delLiteral(slot)
        when (type) {
            in SlotType.BYTE..SlotType.INT -> {
                pushSlotInst(jvmOpcodes.ISTORE, slot)
                frame.setSlot(slot, type)
            }
            SlotType.LONG -> {
                pushSlotInst(jvmOpcodes.LSTORE, slot)
                frame.setSlotWide(slot, type)
            }
            SlotType.FLOAT -> {
                pushSlotInst(jvmOpcodes.FSTORE, slot)
                frame.setSlot(slot, type)
            }
            SlotType.DOUBLE -> {
                pushSlotInst(jvmOpcodes.DSTORE, slot)
                frame.setSlotWide(slot, type)
            }
            SlotType.OBJECT -> {
                pushSlotInst(jvmOpcodes.ASTORE, slot)
                frame.setSlot(slot, type)
            }
            else -> {
                // TODO
            }
        }
    }

    private fun visitReturnVoid() {
        pushSingleInst(jvmOpcodes.RETURN)
    }

    private fun visitReturn(slot: Int, type: SlotType, offset: Int) {
        visitLoad(slot, type, offset)
        when (type) {
            in SlotType.BYTE..SlotType.INT -> {
                pushSingleInst(jvmOpcodes.IRETURN)
            }
            SlotType.LONG -> {
                pushSingleInst(jvmOpcodes.LRETURN)
            }
            SlotType.FLOAT -> {
                pushSingleInst(jvmOpcodes.FRETURN)
            }
            SlotType.DOUBLE -> {
                pushSingleInst(jvmOpcodes.DSTORE)
            }
            SlotType.OBJECT -> {
                pushSingleInst(jvmOpcodes.ARETURN)
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
            pushJumpInst(jvmOpcodes.GOTO, target)
            StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
        }

    }

    private fun visitGetField(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val fieldInfo = FieldInfo.fromDex(dexNode, dalvikInst.index)
        if (fieldInfo.declClass != mthNode.parent.clsInfo) { // determine this field in parent class
            pushFieldInst(jvmOpcodes.GETSTATIC, dalvikInst.index)
        } else {
            val fieldNode = mthNode.parent.searchField(fieldInfo) ?: throw DecodeException("Get field $fieldInfo failed.", offset)
            if (fieldNode.isStatic()) {
                pushFieldInst(jvmOpcodes.GETSTATIC, dalvikInst.index)
            } else {
                pushFieldInst(jvmOpcodes.GETFIELD, dalvikInst.index)
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
                Opcodes.ADD_INT_2ADDR -> pushSingleInst(jvmOpcodes.IADD)
                Opcodes.SUB_INT_2ADDR -> pushSingleInst(jvmOpcodes.ISUB)
                Opcodes.MUL_INT_2ADDR -> pushSingleInst(jvmOpcodes.IMUL)
                Opcodes.DIV_INT_2ADDR -> pushSingleInst(jvmOpcodes.IDIV)
                Opcodes.REM_INT_2ADDR -> pushSingleInst(jvmOpcodes.IREM)
                Opcodes.AND_INT_2ADDR -> pushSingleInst(jvmOpcodes.IAND)
                Opcodes.OR_INT_2ADDR -> pushSingleInst(jvmOpcodes.IOR)
                Opcodes.XOR_INT_2ADDR -> pushSingleInst(jvmOpcodes.IXOR)
                Opcodes.SHL_INT_2ADDR -> pushSingleInst(jvmOpcodes.ISHL)
                Opcodes.SHR_INT_2ADDR -> pushSingleInst(jvmOpcodes.ISHR)
                Opcodes.USHR_INT_2ADDR -> pushSingleInst(jvmOpcodes.IUSHR)

                Opcodes.ADD_FLOAT_2ADDR -> pushSingleInst(jvmOpcodes.FADD)
                Opcodes.SUB_FLOAT_2ADDR -> pushSingleInst(jvmOpcodes.FSUB)
                Opcodes.MUL_FLOAT_2ADDR -> pushSingleInst(jvmOpcodes.FMUL)
                Opcodes.DIV_FLOAT_2ADDR -> pushSingleInst(jvmOpcodes.FDIV)
                Opcodes.REM_FLOAT_2ADDR -> pushSingleInst(jvmOpcodes.FREM)
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
                visitPushOrLdc(dalvikInst.literalInt.toLong(), SlotType.INT, offset)
                visitLoad(regB, SlotType.SHORT, offset)
            } else {
                visitLoad(regB, SlotType.INT, offset)
                visitPushOrLdc(dalvikInst.literalInt.toLong(), SlotType.INT, offset)
            }
            when (dalvikInst.opcode) {
                Opcodes.ADD_INT_LIT8, Opcodes.ADD_INT_LIT16 -> pushSingleInst(jvmOpcodes.IADD)
                Opcodes.RSUB_INT_LIT8, Opcodes.RSUB_INT -> pushSingleInst(jvmOpcodes.ISUB)
                Opcodes.MUL_INT_LIT8, Opcodes.MUL_INT_LIT16 -> pushSingleInst(jvmOpcodes.IMUL)
                Opcodes.DIV_INT_LIT8, Opcodes.DIV_INT_LIT16 -> pushSingleInst(jvmOpcodes.IDIV)
                Opcodes.REM_INT_LIT8, Opcodes.REM_INT_LIT16 -> pushSingleInst(jvmOpcodes.IREM)
                Opcodes.AND_INT_LIT8, Opcodes.AND_INT_LIT16 -> pushSingleInst(jvmOpcodes.IAND)
                Opcodes.OR_INT_LIT8, Opcodes.OR_INT_LIT16 -> pushSingleInst(jvmOpcodes.IOR)
                Opcodes.XOR_INT_LIT8, Opcodes.XOR_INT_LIT16 -> pushSingleInst(jvmOpcodes.IXOR)
                Opcodes.SHL_INT_LIT8 -> pushSingleInst(jvmOpcodes.ISHL)
                Opcodes.SHR_INT_LIT8 -> pushSingleInst(jvmOpcodes.ISHR)
                Opcodes.USHR_INT_LIT8 -> pushSingleInst(jvmOpcodes.IUSHR)
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
            pushSingleInst(jvmOpcodes.ATHROW)
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
                    pushSingleInst(jvmOpcodes.FCMPL + dalvikInst.opcode - Opcodes.CMPL_FLOAT)
                }
                Opcodes.CMPL_DOUBLE, Opcodes.CMPG_DOUBLE -> {
                    visitLoad(regB, SlotType.DOUBLE, offset)
                    visitLoad(regC, SlotType.DOUBLE, offset)
                    pushSingleInst(jvmOpcodes.DCMPL + dalvikInst.opcode - Opcodes.CMPL_DOUBLE)
                }
                Opcodes.CMP_LONG -> {
                    visitLoad(regB, SlotType.LONG, offset)
                    visitLoad(regC, SlotType.LONG, offset)
                    pushSingleInst(jvmOpcodes.LCMP)
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
                pushSingleInst(jvmOpcodes.MONITORENTER)
            } else {
                pushSingleInst(jvmOpcodes.MONITOREXIT)
            }
        } catch (ex: Exception) {

        }
    }

    private fun visitNewInstance(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val nextInst = mthNode.getNextInst(offset) ?: throw DecodeException("New-Instance error", offset)
        if (nextInst.instruction.opcode != Opcodes.INVOKE_DIRECT) throw DecodeException("New-Instance error", offset)
        skipInst = 1
        val mthInfo = MethodInfo.fromDex(dexNode, nextInst.instruction.index)
        pushTypeInst(jvmOpcodes.NEW, mthInfo.declClass.className())
        pushSingleInst(jvmOpcodes.DUP)
        pushInvokeInst(InvokeType.INVOKESPECIAL, nextInst.instruction.index)
        visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
    }

    private fun visitArrayLength(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regB(), SlotType.ARRAY, offset)
        pushSingleInst(jvmOpcodes.ARRAYLENGTH)
        visitStore(SlotType.INT, dalvikInst.regA(), frame)
    }

    private fun visitNewArray(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regB(), SlotType.INT, offset)
        val arrayType = dexNode.getType(dalvikInst.index).getAsArrayType() ?: throw DecodeException("New array type error", offset)
        if (arrayType.subType.getAsObjectType() != null) {
            pushTypeInst(jvmOpcodes.ANEWARRAY, arrayType.subType.getAsObjectType()!!.nameWithSlash())
        } else {
            when (arrayType.subType.getAsBasicType() ?: throw DecodeException("New array is not correct type", offset)) {
                BasicType.BOOLEAN -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_BOOLEAN)
                BasicType.BYTE -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_BYTE)
                BasicType.CHAR -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_CHAR)
                BasicType.SHORT -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_SHORT)
                BasicType.INT -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_INT)
                BasicType.FLOAT -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_FLOAT)
                BasicType.LONG -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_LONG)
                BasicType.DOUBLE -> pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_DOUBLE)
                else -> {
                    throw DecodeException("New array basic type error", offset)
                }
            }
        }
        visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
    }

    private fun visitCheckCast(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.OBJECT, offset)
        val type = dexNode.getType(dalvikInst.index).getAsObjectType()
                ?: throw DecodeException("CheckCast without object type", offset)
        pushTypeInst(jvmOpcodes.CHECKCAST, type.nameWithSlash())
        visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
    }

    private fun visitInstanceOf(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regB(), SlotType.OBJECT, offset)
        val type = dexNode.getType(dalvikInst.index).getAsObjectType()
                ?: throw DecodeException("CheckCast without object type", offset)
        pushTypeInst(jvmOpcodes.INSTANCEOF, type.nameWithSlash())
        visitStore(SlotType.INT, dalvikInst.regA(), frame)
    }

    private fun visitArrayOp(dalvikInst: ThreeRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regB(), SlotType.OBJECT, offset)
        visitLoad(dalvikInst.regC(), SlotType.INT, offset)
        when (dalvikInst.opcode) {
            Opcodes.AGET -> {
                pushSingleInst(jvmOpcodes.IALOAD)
                visitStore(SlotType.INT, dalvikInst.regA(), frame)
            }
            Opcodes.AGET_WIDE -> {

            }
            Opcodes.AGET_OBJECT -> {
                pushSingleInst(jvmOpcodes.AALOAD)
                visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
            }
            Opcodes.AGET_BOOLEAN, Opcodes.AGET_BYTE, Opcodes.AGET_CHAR -> {
                pushSingleInst(jvmOpcodes.BALOAD)
                visitStore(SlotType.BYTE, dalvikInst.regA(), frame)
            }
            Opcodes.AGET_SHORT -> {
                pushSingleInst(jvmOpcodes.SALOAD)
                visitStore(SlotType.SHORT, dalvikInst.regA(), frame)
            }
            Opcodes.APUT -> {
                visitLoad(dalvikInst.regA(), SlotType.INT, offset)
                pushSingleInst(jvmOpcodes.IASTORE)
            }
            Opcodes.APUT -> {
                visitLoad(dalvikInst.regA(), SlotType.INT, offset)
                pushSingleInst(jvmOpcodes.IASTORE)
            }
            Opcodes.APUT_WIDE -> {

            }
            Opcodes.APUT_OBJECT -> {
                visitLoad(dalvikInst.regA(), SlotType.OBJECT, offset)
                pushSingleInst(jvmOpcodes.AASTORE)
            }
            Opcodes.APUT_BOOLEAN, Opcodes.APUT_BYTE, Opcodes.APUT_CHAR -> {
                visitLoad(dalvikInst.regA(), SlotType.BYTE, offset)
                pushSingleInst(jvmOpcodes.BASTORE)
            }
            Opcodes.APUT_SHORT -> {
                visitLoad(dalvikInst.regA(), SlotType.SHORT, offset)
                pushSingleInst(jvmOpcodes.SASTORE)
            }
        }
    }
}