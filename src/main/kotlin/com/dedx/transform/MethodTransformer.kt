package com.dedx.transform

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.*
import com.dedx.dex.pass.CFGBuildPass
import com.dedx.dex.pass.DataFlowAnalysisPass
import com.dedx.dex.pass.DataFlowMethodInfo
import com.dedx.dex.struct.*
import com.dedx.dex.struct.type.BasicType
import com.dedx.utils.DecodeException
import com.dedx.utils.TypeConfliction
import com.google.common.flogger.FluentLogger
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
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

class MethodTransformer(val mthNode: MethodNode, private val clsTransformer: ClassTransformer) {

    companion object {
        private val logger = FluentLogger.forEnclosingClass()
    }

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
    var jvmLabelUse: Int = 0 // mark same label used count
    var jvmLine: Int? = null
    var skipInst = 0 // mark instruction number which skip
    val instMap = HashMap<InstNode, List<JvmInst>>()
    val currentInstList = ArrayList<JvmInst>()
    // use list for exception nested
    val exception2Catch = HashMap<String /*exception type*/, MutableList<Pair<Pair<Int /*start*/, Int /*end*/>, Int /*catch inst*/>>>()
    val catch2Exception = HashMap<Int /*catch inst*/, MutableList<Pair<String /*exception type*/, Pair<Int /*start*/, Int /*end*/>>>>()
    var maxStack = 0
    var maxLocal = 0

    val mthVisit: MethodVisitor by lazy {
        clsTransformer.classWriter.visitMethod(mthNode.accFlags, mthNode.mthInfo.name, mthNode.descriptor, null, null)
    }

    fun visitMethodAnnotation() = apply {
        val values = mthNode.attributes[AttrKey.ANNOTATION]?.getAsAttrValueList() ?: return@apply
        for (value in values) {
            val annoClazz = value.getAsAnnotation() ?: continue
            val annoVisitor = mthVisit.visitAnnotation(annoClazz.type.descriptor(), annoClazz.hasVisibility())
            for (annoValue in annoClazz.values) {
                annoVisitor.visit(annoValue.key, annoValue.value.value)
            }
            annoVisitor.visitEnd()
        }


        val paramAnnoVisit = fun(avs: AttrValueList, i: Int) {
            for (value in avs) {
                val annoClazz = value.getAsAnnotation() ?: continue
                val annoVisitor = mthVisit.visitParameterAnnotation(i, annoClazz.type.descriptor(), annoClazz.hasVisibility())
                for (annoValue in annoClazz.values) {
                    annoVisitor.visit(annoValue.key, annoValue.value.value)
                }
                annoVisitor.visitEnd()
            }
        }
        val paramValues = mthNode.attributes[AttrKey.MTH_PARAMETERS_ANNOTATION]?.getAsAttrValueList() ?: return@apply
        for (i in paramValues.value.indices) {
            val annoValues = paramValues.value[i].getAsAttrValueList() ?: continue
            paramAnnoVisit(annoValues, i)
        }
    }

    fun visitMethodBody(): Boolean {
        if (mthNode.noCode) {
            if (mthNode.isAbstract()) {
                mthVisit.visitEnd()
            }
            return true
        }
        if (mthNode.debugInfoOffset != DexNode.NO_INDEX) {
            try {
                MethodDebugInfoVisitor.visitMethod(mthNode)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log(logInfo())
            }
        }
//        if (clsTransformer.config.optLevel >= Configuration.Optimized) {
//            visitOptimization()
//        } else {
        logger.atInfo().log("Start method body [${logInfo()}]")
            return visitNormal()
//        }
    }

    private fun visitNormal(): Boolean {
        try {
            StackFrame.initInstFrame(mthNode)
            val entryFrame = StackFrame.getFrameOrPut(0)
            for (type in mthNode.argsList) {
                logger.atInfo().log("Set argument [${slotNum(type.regNum)}] ${type.type}")
                entryFrame.pushElement(slotNum(type.regNum), type.type)
            }
            visitExceptionStackFrame()
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
                if (currentInstList.isNotEmpty()) {
                    instMap[inst] = currentInstList.clone() as List<JvmInst>
                    currentInstList.clear()
                }
            }
            visitTryCatchBlock()
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log(logInfo())
            InstTransformer.throwErrorMethod(mthVisit)
            return false
        }
        mthVisit.visitCode()
        try {
            jvmInstManager.visitJvmInst(clsTransformer.config)
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log(logInfo())
            InstTransformer.throwErrorMethod(mthVisit)
            return false
        }
        // TODO: now set COMPUTE_MAX_STACK_AND_LOCAL flag
        mthVisit.visitMaxs(maxStack, maxLocal)
        mthVisit.visitEnd()
        return true
    }

    private fun visitOptimization(): Boolean {
        CFGBuildPass.visit(this)
        dfInfo = DataFlowAnalysisPass.visit(this)
        DataFlowAnalysisPass.livenessAnalyzer(dfInfo!!)
        // TODO tranform by optimize
        return false
    }

    private fun visitExceptionStackFrame() {
        for (tcBlock in mthNode.tryBlockList) {
            val start = tcBlock.instList[0].cursor
            val end = (mthNode.getNextInst(tcBlock.instList.last().cursor)
                    ?: throw DecodeException("try block end out of bounds")).cursor
            for (exec in tcBlock.execHandlers) {
                val catchInst = code(exec.addr) ?: continue
                if (catchInst.instruction.opcode == Opcodes.MOVE_EXCEPTION) {
                    for (type in exec.typeList()) {
                        exception2Catch.putIfAbsent(type ?: Throwable::class.java.name, ArrayList())
                                ?.add(Pair(Pair(start, end), catchInst.cursor))
                                ?: exception2Catch[type ?: Throwable::class.java.name]
                                        ?.add(Pair(Pair(start, end), catchInst.cursor))
                        catch2Exception.putIfAbsent(catchInst.cursor, ArrayList())
                                ?.add(Pair(type ?: Throwable::class.java.name, Pair(start, end)))
                                ?: catch2Exception[catchInst.cursor]
                                        ?.add(Pair(type ?: Throwable::class.java.name, Pair(start, end)))
                    }
                }
            }
        }
    }

    private fun visitTryCatchBlock() {
        for (tcBlock in mthNode.tryBlockList) {
            val start = tcBlock.instList[0]
            val end = mthNode.getNextInst(tcBlock.instList.last().cursor) ?: throw DecodeException("try block end out of bounds")
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
                    val startInst = instMap[start]?.first() ?: throw DecodeException("TryCatch block empty")
                    val endInst = instMap[end]?.last() ?: throw DecodeException("TryCatch block empty")
                    val catchInst = instMap[catchInst]?.first() ?: throw DecodeException("TryCatch block empty")
                    for (type in exec.typeList()) {
                        jvmInstManager.addTryCatchElement(startInst, endInst, catchInst, type)
//                        mthVisit.visitTryCatchBlock(startLabel, endLabel, catchLabel, type)
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

    private fun getStartJvmLabelInst() = if (jvmLabelUse == 0) {
        jvmLabelUse++
        when (jvmLabel) {
            null -> LabelInst()
            else -> LabelInst(jvmLabel as Label)
        }
    } else {
        LabelInst()
    }

    private fun setStartJvmLabelInst(label: Label) {
        jvmLabel = label
        jvmLabelUse = 0
    }

    private fun getStartJvmLine(): Int? {
        if (jvmLine != null) {
            val line: Int = jvmLine!!
            jvmLine = null
            return line
        }
        return null
    }

    private fun pushSingleInst(opcodes: Int) {
        val jvmInst = JvmInst.CreateSingleInst(opcodes, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushSlotInst(opcodes: Int, slot: Int) {
        val jvmInst = JvmInst.CreateSlotInst(opcodes, slot, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushIntInst(opcodes: Int, number: Int) {
        val jvmInst = JvmInst.CreateIntInst(opcodes, number, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushLiteralInst(opcodes: Int, literal: Long, type: SlotType){
        val jvmInst = JvmInst.CreateLiteralInst(opcodes, literal, type, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushTypeInst(opcodes: Int, type: String){
        val jvmInst = JvmInst.CreateTypeInst(opcodes, type, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushConstantInst(opcodes: Int, constIndex: Int){
        val jvmInst = JvmInst.CreateConstantInst(opcodes, constIndex, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushInvokeInst(invokeType: Int, mthIndex: Int){
        val jvmInst = JvmInst.CreateInvokeInst(invokeType, invokeType, mthIndex, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushJumpInst(opcodes: Int, target: Label){
        val jvmInst = JvmInst.CreateJumpInst(opcodes, LabelInst(target), getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushFieldInst(opcodes: Int, fieldIndex: Int){
        val jvmInst = JvmInst.CreateFieldInst(opcodes, fieldIndex, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushShadowInst(opcodes: Int, literal: Long?, vararg regNum: Int): ShadowInst {
        val shadowInst = JvmInst.CreateShadowInst(opcodes, literal, regNum, getStartJvmLabelInst(), getStartJvmLine())
                as ShadowInst
        jvmInstManager.pushJvmInst(shadowInst)
        currentInstList.add(shadowInst)
        return shadowInst
    }
    private fun pushFillArrayDataPayloadInst(slot: Int, target: Int, type: SlotType){
        val jvmInst = JvmInst.CreateFillArrayDataPayloadInst(slot, target, type, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushPackedSwitchPayloadInst(target: Int, defLabel: Label){
        val jvmInst = JvmInst.CreatePackedSwitchPayloadInst(target, LabelInst(defLabel), getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushSparseSwitchPayloadInst(target: Int, defLabel: Label){
        val jvmInst = JvmInst.CreateSparseSwitchPayloadInst(target, LabelInst(defLabel), getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }
    private fun pushMultiANewArrayInsn(typeName: String, num: Int){
        val jvmInst = JvmInst.CreateMultiANewArrayInsn(typeName, num, getStartJvmLabelInst(), getStartJvmLine())
        jvmInstManager.pushJvmInst(jvmInst)
        currentInstList.add(jvmInst)
    }

    private fun DecodedInstruction.regA() = slotNum(a)
    private fun DecodedInstruction.regB() = slotNum(b)
    private fun DecodedInstruction.regC() = slotNum(c)
    private fun DecodedInstruction.regD() = slotNum(d)
    private fun DecodedInstruction.regE() = slotNum(e)

    private fun normalProcess(inst: InstNode) {

        /*should use a safer way of assigning*/
        setStartJvmLabelInst(inst.getLabelOrPut().value)
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
                visitMoveException(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
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
                visitFilledNewArray(dalvikInst, frame, inst.cursor)
            }
            Opcodes.FILLED_NEW_ARRAY_RANGE -> {
                visitFilledNewArrayRange(dalvikInst as RegisterRangeDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.FILL_ARRAY_DATA -> {
                visitPayloadInst(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.THROW -> visitThrow(dalvikInst as OneRegisterDecodedInstruction, inst.cursor)
            in Opcodes.GOTO..Opcodes.GOTO_32 -> visitGoto(dalvikInst as ZeroRegisterDecodedInstruction, inst.cursor)
            Opcodes.PACKED_SWITCH -> {
                visitPayloadInst(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            }
            Opcodes.SPARSE_SWITCH -> {
                visitPayloadInst(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
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
            in Opcodes.IGET..Opcodes.IPUT_SHORT -> visitInstanceField(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.SGET..Opcodes.SPUT_SHORT -> visitStaticField(dalvikInst as OneRegisterDecodedInstruction, frame, inst.cursor)
            Opcodes.INVOKE_VIRTUAL -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKEVIRTUAL, frame, inst.cursor)
            Opcodes.INVOKE_SUPER -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESPECIAL, frame, inst.cursor)
            Opcodes.INVOKE_DIRECT -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESPECIAL, frame, inst.cursor)
            Opcodes.INVOKE_STATIC -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKESTATIC, frame, inst.cursor)
            Opcodes.INVOKE_INTERFACE -> newestReturn = visitInvoke(dalvikInst, InvokeType.INVOKEINTERFACE, frame, inst.cursor)
            Opcodes.INVOKE_VIRTUAL_RANGE -> newestReturn = visitInvokeRange(dalvikInst as RegisterRangeDecodedInstruction,
                    InvokeType.INVOKEVIRTUAL, frame, inst.cursor)
            Opcodes.INVOKE_SUPER_RANGE -> newestReturn = visitInvokeRange(dalvikInst as RegisterRangeDecodedInstruction,
                    InvokeType.INVOKESPECIAL, frame, inst.cursor)
            Opcodes.INVOKE_DIRECT_RANGE -> newestReturn = visitInvokeRange(dalvikInst as RegisterRangeDecodedInstruction,
                    InvokeType.INVOKESPECIAL, frame, inst.cursor)
            Opcodes.INVOKE_STATIC_RANGE -> newestReturn = visitInvokeRange(dalvikInst as RegisterRangeDecodedInstruction,
                    InvokeType.INVOKESTATIC, frame, inst.cursor)
            Opcodes.INVOKE_INTERFACE_RANGE -> newestReturn = visitInvokeRange(dalvikInst as RegisterRangeDecodedInstruction,
                    InvokeType.INVOKEINTERFACE, frame, inst.cursor)
            in Opcodes.NEG_INT..Opcodes.INT_TO_SHORT -> visitUnop(dalvikInst as TwoRegisterDecodedInstruction, frame, inst.cursor)
            in Opcodes.ADD_INT..Opcodes.REM_DOUBLE -> visitBinOp(dalvikInst as ThreeRegisterDecodedInstruction, frame, inst.cursor)
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
        var skip = false
        for (argPos in 0 until dalvikInst.registerCount) {
            if (skip) {
                skip = false
                continue
            }
            val type = if (argPos == 0 && thisPos == 1) SlotType.OBJECT else SlotType.convert(mthInfo.args[argPos - thisPos])
                    ?: throw DecodeException("invoke argument type error.", offset)
            if (type == SlotType.DOUBLE || type == SlotType.LONG) {
                skip = true
            }
            when (argPos) {
                0 -> visitLoad(dalvikInst.regA(), type, offset)
                1 -> visitLoad(dalvikInst.regB(), type, offset)
                2 -> visitLoad(dalvikInst.regC(), type, offset)
                3 -> visitLoad(dalvikInst.regD(), type, offset)
                4 -> visitLoad(dalvikInst.regE(), type, offset)
                else -> throw DecodeException("invoke instruction register number error.", offset)
            }
        }
        pushInvokeInst(invokeType, dalvikInst.index)
        return SlotType.convert(mthInfo.retType)
    }

    private fun visitInvokeRange(dalvikInst: RegisterRangeDecodedInstruction, invokeType: Int, frame: StackFrame, offset: Int): SlotType? {
        val mthInfo = MethodInfo.fromDex(dexNode, dalvikInst.index)
        for (i in 0 until dalvikInst.registerCount) {
            val slot = slotNum(dalvikInst.a + i)
            visitLoad(slot, frame.getSlot(slot)?.getTypeOrNull() ?: throw DecodeException("Empty type in slot [$slot]"), offset)
        }
        pushInvokeInst(invokeType, dalvikInst.index)
        return SlotType.convert(mthInfo.retType)
    }

    private fun visitConst(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val slot = dalvikInst.regA()
        when (dalvikInst.opcode) {
            Opcodes.CONST_4 -> {
                frame.setSlotLiteral(slot, dalvikInst.literalByte.toLong(), SymIdentifier.NumberLiteral)
            }
            Opcodes.CONST_16 -> {
                frame.setSlotLiteral(slot, dalvikInst.literalUnit.toLong(), SymIdentifier.NumberLiteral)
            }
            in Opcodes.CONST..Opcodes.CONST_HIGH16 -> {
                frame.setSlotLiteral(slot, dalvikInst.literalInt.toLong(), SymIdentifier.NumberLiteral)
            }
            in Opcodes.CONST_WIDE_16..Opcodes.CONST_WIDE_HIGH16 -> {
                frame.setSlotLiteral(slot, dalvikInst.literal, SymIdentifier.NumberLiteral) // also double type
            }
            Opcodes.CONST_STRING, Opcodes.CONST_STRING_JUMBO -> {
                frame.setSlotLiteral(slot, dalvikInst.index.toLong(), SymIdentifier.StringIndex) // constant pool index as int type
            }
            Opcodes.CONST_CLASS -> {
                frame.setSlotLiteral(slot, dalvikInst.index.toLong(), SymIdentifier.SymbolTypeIndex) // constant pool index as int type
            }
        }
    }

    private fun visitIfStmt(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        visitLoad(dalvikInst.regB(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLabelOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        pushJumpInst(dalvikInst.opcode - Opcodes.IF_EQ + jvmOpcodes.IF_ICMPEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitIfStmt(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.INT, offset)
        val target = code(dalvikInst.target)?.getLabelOrPut()?.value
                ?: throw DecodeException("${dalvikInst.target} has no lable", offset)
        pushJumpInst(dalvikInst.opcode - Opcodes.IF_EQZ + jvmOpcodes.IFEQ, target)
        StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
    }

    private fun visitLoad(slot: Int, slotType: SlotType, offset: Int): SlotType {
        if (StackFrame.getFrameOrExcept(offset).isConstant(slot)) {
            visitPushOrLdc(slot, slotType, offset)
            return slotType
        }
        when (slotType) {
            SlotType.BOOLEAN,
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

    private fun StackFrame.isConstant(slot: Int) = getSlot(slot) == null || getSlot(slot)?.isSymbolType() == false

    private fun visitPushOrLdc(slot: Int, slotType: SlotType, offset: Int) {
        try {
            val frame = StackFrame.getFrameOrExcept(offset)
            val info = frame.getSlot(slot) ?: throw DecodeException("slot <$slot> is empty [$offset]")
            val literal = info.getNumber()
            if (info.isNumberLiteral()) {
                visitPushOrLdc(literal, slotType, offset)
            }
            if (info.isStringIndex()) {
                pushConstantInst(jvmOpcodes.LDC, literal.toInt())
            }
            if (info.isSymbolTypeIndex()) {
                pushTypeInst(jvmOpcodes.LDC, dexNode.getType(literal.toInt()).descriptor())
            }
        } catch (de: DecodeException) {
            throw DecodeException("LDC instruction error", offset, de)
        }
    }

    private fun visitPushOrLdc(literal: Long, slotType: SlotType, offset: Int) {
        when (slotType) {
            SlotType.BOOLEAN -> pushSingleInst(jvmOpcodes.ICONST_0 + literal.toInt())
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
            SlotType.OBJECT -> {
                when (literal) {
                    0L -> pushSingleInst(jvmOpcodes.ACONST_NULL)
                    else -> throw DecodeException("Const object error")
                }
            }
            else -> {
                throw DecodeException("Const type error", offset)
            }
        }
    }

    private fun visitStore(type: SlotType, slot: Int, frame: StackFrame) {
        when (type) {
            SlotType.BOOLEAN,
            in SlotType.BYTE..SlotType.INT -> {
                pushSlotInst(jvmOpcodes.ISTORE, slot)
                frame.setSlot(slot, type)
            }
            SlotType.LONG -> {
                pushSlotInst(jvmOpcodes.LSTORE, slot)
                frame.setSlotWide(slot, SlotType.LONG)
            }
            SlotType.FLOAT -> {
                pushSlotInst(jvmOpcodes.FSTORE, slot)
                frame.setSlot(slot, SlotType.FLOAT)
            }
            SlotType.DOUBLE -> {
                pushSlotInst(jvmOpcodes.DSTORE, slot)
                frame.setSlotWide(slot, SlotType.DOUBLE)
            }
            SlotType.OBJECT -> {
                pushSlotInst(jvmOpcodes.ASTORE, slot)
                frame.setSlot(slot, SlotType.OBJECT)
            }
            SlotType.ARRAY -> {
                pushSlotInst(jvmOpcodes.ASTORE, slot)
                frame.setSlot(slot, SlotType.ARRAY)
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
            SlotType.BOOLEAN,
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
            SlotType.OBJECT, SlotType.ARRAY -> {
                pushSingleInst(jvmOpcodes.ARETURN)
            }
            else -> {
                throw DecodeException("return unknow type: $type [$offset]")
            }
        }
    }

    private fun visitGoto(dalvikInst: ZeroRegisterDecodedInstruction, offset: Int) {
        val targetCode = code(dalvikInst.target) ?: throw DecodeException("Goto target [${dalvikInst.target}] is error", offset)
        if (targetCode.instruction.opcode in Opcodes.RETURN..Opcodes.RETURN_OBJECT) {
            visitReturn(targetCode.instruction.regA(), SlotType.convert(mthNode.getReturnType())!!, offset)
        } else {
            val target = targetCode.getLabelOrPut().value
            pushJumpInst(jvmOpcodes.GOTO, target)
            StackFrame.getFrameOrPut(dalvikInst.target).addPreFrame(offset)
        }

    }

    private fun visitInstanceField(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val fieldInfo = FieldInfo.fromDex(dexNode, dalvikInst.index)
        visitLoad(dalvikInst.regB(), SlotType.OBJECT, offset)
        if (dalvikInst.opcode < Opcodes.IPUT) {
            pushFieldInst(jvmOpcodes.GETFIELD, dalvikInst.index)
            visitStore(SlotType.convert(fieldInfo.type)!!, dalvikInst.regA(), frame)
        } else {
            visitLoad(dalvikInst.regA(), SlotType.convert(fieldInfo.type)!!, offset)
            pushFieldInst(jvmOpcodes.PUTFIELD, dalvikInst.index)
        }
    }

    private fun visitStaticField(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val fieldInfo = FieldInfo.fromDex(dexNode, dalvikInst.index)
        if (dalvikInst.opcode < Opcodes.SPUT) {
            pushFieldInst(jvmOpcodes.GETSTATIC, dalvikInst.index)
            visitStore(SlotType.convert(fieldInfo.type)!!, dalvikInst.regA(), frame)
        } else {
            visitLoad(dalvikInst.regA(), SlotType.convert(fieldInfo.type)!!, offset)
            pushFieldInst(jvmOpcodes.PUTSTATIC, dalvikInst.index)
        }
    }

    private fun visitBinOp2Addr(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            var type = SlotType.INT
            if (dalvikInst.opcode >= Opcodes.ADD_FLOAT_2ADDR) {
                type = SlotType.FLOAT
            }
            val regA = dalvikInst.regA()
            val regB = dalvikInst.regB()
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
        var type = SlotType.LONG
        if (dalvikInst.opcode >= Opcodes.ADD_DOUBLE_2ADDR) {
            type = SlotType.DOUBLE
        }
        val regA = dalvikInst.regA()
        val regB = dalvikInst.regB()
        visitLoad(regA, type, offset)
        visitLoad(regB, type, offset)
        when (dalvikInst.opcode) {
            Opcodes.ADD_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LADD)
            Opcodes.SUB_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LSUB)
            Opcodes.MUL_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LMUL)
            Opcodes.DIV_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LDIV)
            Opcodes.REM_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LREM)
            Opcodes.AND_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LAND)
            Opcodes.OR_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LOR)
            Opcodes.XOR_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LXOR)
            Opcodes.SHL_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LSHL)
            Opcodes.SHR_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LSHR)
            Opcodes.USHR_LONG_2ADDR -> pushSingleInst(jvmOpcodes.LUSHR)

            Opcodes.ADD_DOUBLE_2ADDR -> pushSingleInst(jvmOpcodes.DADD)
            Opcodes.SUB_DOUBLE_2ADDR -> pushSingleInst(jvmOpcodes.DSUB)
            Opcodes.MUL_DOUBLE_2ADDR -> pushSingleInst(jvmOpcodes.DMUL)
            Opcodes.DIV_DOUBLE_2ADDR -> pushSingleInst(jvmOpcodes.DDIV)
            Opcodes.REM_DOUBLE_2ADDR -> pushSingleInst(jvmOpcodes.DREM)
        }
        visitStore(type, regA, frame)
    }

    private fun visitBinOp(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        try {
            val regA = dalvikInst.regA()
            val regB = dalvikInst.regB()
            if (dalvikInst.opcode == Opcodes.RSUB_INT || dalvikInst.opcode == Opcodes.RSUB_INT_LIT8) {
                visitPushOrLdc(dalvikInst.literalInt.toLong(), SlotType.INT, offset)
                visitLoad(regB, SlotType.INT, offset)
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

    private fun visitUnop(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        var fromType = SlotType.INT
        var toType = SlotType.INT
        var opcodes = 0
        when (dalvikInst.opcode) {
            Opcodes.NEG_INT -> {
                opcodes = jvmOpcodes.INEG
                fromType = SlotType.INT
                toType = SlotType.INT
            }
            Opcodes.NOT_INT -> {}
            Opcodes.NEG_LONG -> {
                opcodes = jvmOpcodes.LNEG
                fromType = SlotType.LONG
                toType = SlotType.LONG
            }
            Opcodes.NOT_LONG -> {}
            Opcodes.NEG_FLOAT -> {
                opcodes = jvmOpcodes.FNEG
                fromType = SlotType.FLOAT
                toType = SlotType.FLOAT
            }
            Opcodes.NEG_DOUBLE -> {
                opcodes = jvmOpcodes.DNEG
                fromType = SlotType.DOUBLE
                toType = SlotType.DOUBLE
            }
            Opcodes.INT_TO_LONG -> {
                opcodes = jvmOpcodes.I2L
                fromType = SlotType.INT
                toType = SlotType.LONG
            }
            Opcodes.INT_TO_FLOAT -> {
                opcodes = jvmOpcodes.I2F
                fromType = SlotType.INT
                toType = SlotType.FLOAT
            }
            Opcodes.INT_TO_DOUBLE -> {
                opcodes = jvmOpcodes.I2D
                fromType = SlotType.INT
                toType = SlotType.DOUBLE
            }
            Opcodes.LONG_TO_INT -> {
                opcodes = jvmOpcodes.L2I
                fromType = SlotType.LONG
                toType = SlotType.INT
            }
            Opcodes.LONG_TO_FLOAT -> {
                opcodes = jvmOpcodes.L2F
                fromType = SlotType.LONG
                toType = SlotType.FLOAT
            }
            Opcodes.LONG_TO_DOUBLE -> {
                opcodes = jvmOpcodes.L2D
                fromType = SlotType.LONG
                toType = SlotType.DOUBLE
            }
            Opcodes.FLOAT_TO_INT -> {
                opcodes = jvmOpcodes.F2I
                fromType = SlotType.FLOAT
                toType = SlotType.INT
            }
            Opcodes.FLOAT_TO_LONG -> {
                opcodes = jvmOpcodes.F2L
                fromType = SlotType.FLOAT
                toType = SlotType.LONG
            }
            Opcodes.FLOAT_TO_DOUBLE -> {
                opcodes = jvmOpcodes.F2D
                fromType = SlotType.FLOAT
                toType = SlotType.DOUBLE
            }
            Opcodes.DOUBLE_TO_INT -> {
                opcodes = jvmOpcodes.D2I
                fromType = SlotType.DOUBLE
                toType = SlotType.INT
            }
            Opcodes.DOUBLE_TO_LONG -> {
                opcodes = jvmOpcodes.D2L
                fromType = SlotType.DOUBLE
                toType = SlotType.LONG
            }
            Opcodes.DOUBLE_TO_FLOAT -> {
                opcodes = jvmOpcodes.D2F
                fromType = SlotType.DOUBLE
                toType = SlotType.FLOAT
            }
            Opcodes.INT_TO_BYTE -> {
                opcodes = jvmOpcodes.I2B
                fromType = SlotType.INT
                toType = SlotType.BYTE
            }
            Opcodes.INT_TO_CHAR -> {
                opcodes = jvmOpcodes.I2C
                fromType = SlotType.INT
                toType = SlotType.CHAR
            }
            Opcodes.INT_TO_SHORT -> {
                opcodes = jvmOpcodes.I2S
                fromType = SlotType.INT
                toType = SlotType.SHORT
            }
        }
        visitLoad(dalvikInst.regB(), fromType, offset)
        pushSingleInst(opcodes)
        visitStore(toType, dalvikInst.regA(), frame)
    }

    private fun visitBinOp(dalvikInst: ThreeRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val regA = dalvikInst.regA()
        val regB = dalvikInst.regB()
        val regC = dalvikInst.regC()
        var type = SlotType.INT
        if (dalvikInst.opcode >= Opcodes.ADD_LONG && dalvikInst.opcode <= Opcodes.USHR_LONG) {
            type = SlotType.LONG
        } else if (dalvikInst.opcode >= Opcodes.ADD_FLOAT && dalvikInst.opcode <= Opcodes.REM_FLOAT) {
            type = SlotType.FLOAT
        } else if (dalvikInst.opcode >= Opcodes.ADD_DOUBLE && dalvikInst.opcode <= Opcodes.REM_DOUBLE) {
            type = SlotType.DOUBLE
        }
        visitLoad(regB, type, offset)
        visitLoad(regC, type, offset)
        when (dalvikInst.opcode) {
            Opcodes.ADD_INT -> pushSingleInst(jvmOpcodes.IADD)
            Opcodes.SUB_INT -> pushSingleInst(jvmOpcodes.ISUB)
            Opcodes.MUL_INT -> pushSingleInst(jvmOpcodes.IMUL)
            Opcodes.DIV_INT -> pushSingleInst(jvmOpcodes.IDIV)
            Opcodes.REM_INT -> pushSingleInst(jvmOpcodes.IREM)
            Opcodes.AND_INT -> pushSingleInst(jvmOpcodes.IAND)
            Opcodes.OR_INT -> pushSingleInst(jvmOpcodes.IOR)
            Opcodes.XOR_INT -> pushSingleInst(jvmOpcodes.IXOR)
            Opcodes.SHL_INT -> pushSingleInst(jvmOpcodes.ISHL)
            Opcodes.SHR_INT -> pushSingleInst(jvmOpcodes.ISHR)
            Opcodes.USHR_INT -> pushSingleInst(jvmOpcodes.IUSHR)

            Opcodes.ADD_LONG -> pushSingleInst(jvmOpcodes.LADD)
            Opcodes.SUB_LONG -> pushSingleInst(jvmOpcodes.LSUB)
            Opcodes.MUL_LONG -> pushSingleInst(jvmOpcodes.LMUL)
            Opcodes.DIV_LONG -> pushSingleInst(jvmOpcodes.LDIV)
            Opcodes.REM_LONG -> pushSingleInst(jvmOpcodes.LREM)
            Opcodes.AND_LONG -> pushSingleInst(jvmOpcodes.LAND)
            Opcodes.OR_LONG -> pushSingleInst(jvmOpcodes.LOR)
            Opcodes.XOR_LONG -> pushSingleInst(jvmOpcodes.LXOR)
            Opcodes.SHL_LONG -> pushSingleInst(jvmOpcodes.LSHL)
            Opcodes.SHR_LONG -> pushSingleInst(jvmOpcodes.LSHR)
            Opcodes.USHR_LONG -> pushSingleInst(jvmOpcodes.LUSHR)

            Opcodes.ADD_FLOAT -> pushSingleInst(jvmOpcodes.FADD)
            Opcodes.SUB_FLOAT -> pushSingleInst(jvmOpcodes.FSUB)
            Opcodes.MUL_FLOAT -> pushSingleInst(jvmOpcodes.FMUL)
            Opcodes.DIV_FLOAT -> pushSingleInst(jvmOpcodes.FDIV)
            Opcodes.REM_FLOAT -> pushSingleInst(jvmOpcodes.FREM)

            Opcodes.ADD_DOUBLE -> pushSingleInst(jvmOpcodes.DADD)
            Opcodes.SUB_DOUBLE -> pushSingleInst(jvmOpcodes.DSUB)
            Opcodes.MUL_DOUBLE -> pushSingleInst(jvmOpcodes.DMUL)
            Opcodes.DIV_DOUBLE -> pushSingleInst(jvmOpcodes.DDIV)
            Opcodes.REM_DOUBLE -> pushSingleInst(jvmOpcodes.DREM)
        }
        visitStore(type, regA, frame)
    }

    private fun visitMove(dalvikInst: TwoRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val regB = dalvikInst.regB()
        var slotType = SlotType.INT
        if (frame.isConstant(regB)) {
            visitLoad(regB, SlotType.INT, offset)
        } else {
            slotType = visitLoad(regB, frame.getSlot(regB)?.getTypeOrNull()
                    ?: throw DecodeException("Empty type in slot [$regB] offset[$offset]"), offset)
        }
        visitStore(slotType, dalvikInst.regA(), frame)
    }

    private fun visitThrow(dalvikInst: OneRegisterDecodedInstruction, offset: Int) {
        try {
            val regA = dalvikInst.regA()
//            StackFrame.checkType(SlotType.OBJECT, offset, regA)
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
        val clazz = dexNode.getType(dalvikInst.index).getAsObjectType() ?: throw DecodeException("New-Instance error", offset)
        pushTypeInst(jvmOpcodes.NEW, clazz.nameWithSlash())
        pushSingleInst(jvmOpcodes.DUP)
        var nextInst = mthNode.getNextInst(offset) ?: throw DecodeException("New-Instance error", offset)
        skipInst = 1
        while (nextInst.instruction.opcode != Opcodes.INVOKE_DIRECT) {
            normalProcess(nextInst)
            nextInst = mthNode.getNextInst(nextInst.cursor) ?: throw DecodeException("New-Instance error", offset)
            skipInst ++
        }
        val mthInfo = MethodInfo.fromDex(dexNode, nextInst.instruction.index)
        val nextFrame = StackFrame.getFrameOrExcept(nextInst.cursor).merge()
        // constructor ignore this pointer
        for(i in 1 until nextInst.instruction.registerCount) {
            when (i) {
                1 -> visitLoad(nextInst.instruction.regB(), SlotType.convert(mthInfo.args[i - 1])!!, nextInst.cursor)
                2 -> visitLoad(nextInst.instruction.regC(), SlotType.convert(mthInfo.args[i - 1])!!, nextInst.cursor)
                3 -> visitLoad(nextInst.instruction.regD(), SlotType.convert(mthInfo.args[i - 1])!!, nextInst.cursor)
                4 -> visitLoad(nextInst.instruction.regE(), SlotType.convert(mthInfo.args[i - 1])!!, nextInst.cursor)
                else -> throw DecodeException("invoke instruction register number error.", nextInst.cursor)
            }
        }
        pushInvokeInst(InvokeType.INVOKESPECIAL, nextInst.instruction.index)
        visitStore(SlotType.OBJECT, dalvikInst.regA(), nextFrame)
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
            visitStoreArray(SlotType.OBJECT, dalvikInst.regA(), frame)
        } else {
            when (arrayType.subType.getAsBasicType() ?: throw DecodeException("New array is not correct type", offset)) {
                BasicType.BOOLEAN -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_BOOLEAN)
                    visitStoreArray(SlotType.BOOLEAN, dalvikInst.regA(), frame)
                }
                BasicType.BYTE -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_BYTE)
                    visitStoreArray(SlotType.BYTE, dalvikInst.regA(), frame)
                }
                BasicType.CHAR -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_CHAR)
                    visitStoreArray(SlotType.CHAR, dalvikInst.regA(), frame)
                }
                BasicType.SHORT -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_SHORT)
                    visitStoreArray(SlotType.SHORT, dalvikInst.regA(), frame)
                }
                BasicType.INT -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_INT)
                    visitStoreArray(SlotType.INT, dalvikInst.regA(), frame)
                }
                BasicType.FLOAT -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_FLOAT)
                    visitStoreArray(SlotType.FLOAT, dalvikInst.regA(), frame)
                }
                BasicType.LONG -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_LONG)
                    visitStoreArray(SlotType.LONG, dalvikInst.regA(), frame)
                }
                BasicType.DOUBLE -> {
                    pushIntInst(jvmOpcodes.NEWARRAY, jvmOpcodes.T_DOUBLE)
                    visitStoreArray(SlotType.DOUBLE, dalvikInst.regA(), frame)
                }
                else -> {
                    throw DecodeException("New array basic type error", offset)
                }
            }
        }
    }

    private fun visitStoreArray(type: SlotType, slot: Int, frame: StackFrame) {
        pushSlotInst(jvmOpcodes.ASTORE, slot)
        frame.setSlotArray(slot, type)
    }

    private fun visitCheckCast(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        visitLoad(dalvikInst.regA(), SlotType.OBJECT, offset)
        val type = dexNode.getType(dalvikInst.index)
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
                val arrayType = frame.getArrayTypeExpect(dalvikInst.regB()).lastType()
                when (arrayType) {
                    SlotType.INT -> pushSingleInst(jvmOpcodes.IALOAD)
                    SlotType.FLOAT -> pushSingleInst(jvmOpcodes.FALOAD)
                    else -> throw DecodeException("Error array type: $arrayType for Opcodes.AGET", offset)
                }
                visitStore(arrayType, dalvikInst.regA(), frame)
            }
            Opcodes.AGET_WIDE -> {
                val arrayType = frame.getArrayTypeExpect(dalvikInst.regB()).lastType()
                when (arrayType) {
                    SlotType.LONG -> pushSingleInst(jvmOpcodes.LALOAD)
                    SlotType.DOUBLE -> pushSingleInst(jvmOpcodes.DALOAD)
                    else -> throw DecodeException("Error array type: $arrayType for Opcodes.AGET_WIDR", offset)
                }
                visitStore(arrayType, dalvikInst.regA(), frame)
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
                val arrayType = frame.getArrayTypeExpect(dalvikInst.regB()).lastType()
                visitLoad(dalvikInst.regA(), arrayType, offset)
                when (arrayType) {
                    SlotType.INT -> pushSingleInst(jvmOpcodes.IASTORE)
                    SlotType.FLOAT -> pushSingleInst(jvmOpcodes.FASTORE)
                    else -> throw DecodeException("Error array type: $arrayType for Opcode.APUT", offset)
                }
            }
            Opcodes.APUT_WIDE -> {
                val arrayType = frame.getArrayTypeExpect(dalvikInst.regB()).lastType()
                visitLoad(dalvikInst.regA(), arrayType, offset)
                when (arrayType) {
                    SlotType.LONG -> pushSingleInst(jvmOpcodes.LASTORE)
                    SlotType.DOUBLE -> pushSingleInst(jvmOpcodes.DASTORE)
                    else -> throw DecodeException("Error array type: $arrayType for Opcode.APUT_WIDE", offset)
                }
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

    private fun visitPayloadInst(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        val slot = dalvikInst.regA()
        when (dalvikInst.opcode) {
            Opcodes.FILL_ARRAY_DATA -> pushFillArrayDataPayloadInst(slot, dalvikInst.target,
                    frame.getArrayTypeExpect(slot).lastType())
            Opcodes.PACKED_SWITCH -> {
                visitLoad(slot, frame.getSlot(slot)?.getTypeOrNull() ?: throw DecodeException("Empty slot type [$slot] for PACKED_SWITCH", offset), offset)
                val defLabel = mthNode.getNextInst(offset)?.getLabelOrPut()?.value ?: throw DecodeException("No default label", offset)
                pushPackedSwitchPayloadInst(dalvikInst.target, defLabel)
            }
            Opcodes.SPARSE_SWITCH -> {
                visitLoad(slot, frame.getSlot(slot)?.getTypeOrNull() ?: throw DecodeException("Empty slot type [$slot] for SPARSE_SWITCH", offset), offset)
                val defLabel = mthNode.getNextInst(offset)?.getLabelOrPut()?.value ?: throw DecodeException("No default label", offset)
                pushSparseSwitchPayloadInst(dalvikInst.target, defLabel)
            }
        }
    }

    private fun visitFilledNewArray(dalvikInst: DecodedInstruction, frame: StackFrame, offset: Int) {
        for (i in 0 until dalvikInst.registerCount) {
            when (i) {
                0 -> visitPushOrLdc(dalvikInst.regA(), SlotType.INT, offset)
                1 -> visitPushOrLdc(dalvikInst.regB(), SlotType.INT, offset)
                2 -> visitPushOrLdc(dalvikInst.regC(), SlotType.INT, offset)
                3 -> visitPushOrLdc(dalvikInst.regD(), SlotType.INT, offset)
                4 -> visitPushOrLdc(dalvikInst.regE(), SlotType.INT, offset)
            }
        }
        pushMultiANewArrayInsn("I", dalvikInst.registerCount)
    }

    private fun visitFilledNewArrayRange(dalvikInst: RegisterRangeDecodedInstruction, frame: StackFrame, offset: Int) {
        for (i in 0 until dalvikInst.registerCount) {
            visitPushOrLdc(slotNum(dalvikInst.a + i), SlotType.INT, offset)
        }
        pushMultiANewArrayInsn("I", dalvikInst.registerCount)
    }

    private fun visitMoveException(dalvikInst: OneRegisterDecodedInstruction, frame: StackFrame, offset: Int) {
        // now only merge try-catch block `start` inst
        // TODO need analysis inst which can throw exception
        catch2Exception[offset]?.forEach {
            frame.addPreFrame(it.second.first)
        }
        frame.merge()
        visitStore(SlotType.OBJECT, dalvikInst.regA(), frame)
    }

    private fun logInfo() = "${mthNode.mthInfo}"
}