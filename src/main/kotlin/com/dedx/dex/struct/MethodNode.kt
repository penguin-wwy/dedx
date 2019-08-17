package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.Code
import com.android.dx.io.OpcodeInfo
import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.android.dx.io.instructions.ShortArrayCodeInput
import com.dedx.dex.struct.type.DalvikAnnotationDefault
import com.dedx.dex.struct.type.TypeBox
import com.dedx.dex.struct.type.isSystemCommentType
import com.dedx.utils.DecodeException
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class MethodNode(val parent: ClassNode, val mthData: ClassData.Method, val isVirtual: Boolean): AccessInfo, AttrNode {
    override val accFlags = mthData.accessFlags
    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()

    val mthInfo = MethodInfo.fromDex(parent.parent, mthData.methodIndex)
    val descriptor = mthInfo.parseSignature()
    val argsList = ArrayList<InstArgNode>()
    var thisArg: InstArgNode? = null
    val tryBlockList = ArrayList<TryCatchBlock>()
    val catchList = ArrayList<ExceptionHandler>()
    val sysAnnoMap = HashMap<TypeBox, Annotation>()

    val noCode = mthData.codeOffset == 0

    var regsCount: Int = 0
    var ins: Int = 0
    var outs: Int = 0
    var codeSize: Int = 0
    var debugInfoOffset: Int = 0
    var mthCode: Code? = null
    var codeList = emptyArray<InstNode?>()

    fun load() {
        try {
            if (noCode) {
                regsCount = 0
                codeSize = 0
                initMethodTypes()
                return
            }
            mthCode = parent.parent.dex.readCode(mthData)
            val instructions = mthCode!!.instructions
            codeList = arrayOfNulls(instructions.size)
            val codeInput = ShortArrayCodeInput(instructions)
            try {
                while (codeInput.hasMore()) {
                    val cursor = codeInput.cursor()
                    codeList[cursor] = InstNode(cursor, decodeRawInsn(codeInput))
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            regsCount = mthCode!!.registersSize
            ins = mthCode!!.insSize
            outs = mthCode!!.outsSize
            codeSize = codeList.size
            debugInfoOffset = mthCode!!.debugInfoOffset
            initMethodTypes()
            initTryCatches(mthCode!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun decodeRawInsn(codeInput: ShortArrayCodeInput): DecodedInstruction {
        val opcodeUnit = codeInput.read()
        val opcode = Opcodes.extractOpcodeFromUnit(opcodeUnit)
        try {
            return OpcodeInfo.get(opcode).format.decode(opcodeUnit, codeInput)
        } catch (e: IllegalArgumentException) {
            return OpcodeInfo.NOP.format.decode(opcodeUnit, codeInput)
        }
    }

    private fun initTryCatches(mthCode: Code) {
        val catchBlocks = mthCode.catchHandlers
        val tryList = mthCode.tries
        if (catchBlocks.isEmpty() && tryList.isEmpty()) {
            return
        }
        var handlerCount = 0
        val addrs = HashSet<Int>()
        for (handler in catchBlocks) {
            val tcBlock = TryCatchBlock()
            tryBlockList.add(tcBlock)
            for (i in 0 until handler.addresses.size) {
                tcBlock.addHandler(this, handler.addresses[i], ClassInfo.fromDex(dex(), handler.typeIndexes[i]))
                addrs.add(handler.addresses[i])
                handlerCount++
            }
            if (handler.catchAllAddress > 0) {
                tcBlock.addHandler(this, handler.catchAllAddress, null)
                handlerCount++
            }
        }

        if ((handlerCount > 0) && (handlerCount != addrs.size)) {
            for (outer in tryBlockList) {
                for (inner in tryBlockList) {
                    if ((outer != inner) && inner.containsAllHandlers(outer)) {
                        inner.removeSameHandlers(outer)
                    }
                }
            }
        }

        for (exec in catchList) {
//            codeList[exec.addr]?.setValue()
        }

        for (oneTry in tryList) {
            val catchBlock = tryBlockList[oneTry.catchHandlerIndex]
            var offset = oneTry.startAddress
            val end = offset + oneTry.instructionCount - 1

            val tryEntry = codeList[offset] ?: throw DecodeException("Try block first instruction is null.")
            tryEntry.setTryEntry(catchBlock)
            offset++
            while ((offset <= end) && (offset >= 0)) {
                val insn = codeList[offset]
                if (insn != null) {
                    catchBlock.instList.add(insn)
                }
                offset++
            }
        }
    }

    private fun initMethodTypes() {
        var argRegOff = regsCount - ins
        if (!isStatic()) {
            thisArg = InstArgNode(argRegOff, parent.clsInfo.type)
            argRegOff++
            argsList.add(thisArg!!)
        }
        if (noCode) {
            return
        }
        for (args in mthInfo.args) {
            if (argRegOff >= regsCount) {
                throw DecodeException("regs count less argument count in $mthInfo")
            }
            argsList.add(InstArgNode(argRegOff, args))
            argRegOff++
        }
    }

    fun getArguments(includeThis: Boolean): List<InstArgNode> {
        if (includeThis && (thisArg != null)) {
            val result = ArrayList<InstArgNode>()
            result.add(thisArg!!)
            result.addAll(argsList)
            return result
        }
        return argsList
    }

    fun dex(): DexNode {
        return parent.parent
    }

    fun setLineNumber(num: Int) {
        attributes[AttrKey.LINENUMBER] = AttrValue(Enc.ENC_INT, num)
    }

    fun getPrevInst(index: Int): InstNode? {
        var offset = index - 1
        while (offset >= 0) {
            if (codeList[offset] != null) {
                return codeList[offset]
            } else {
                offset--
            }
        }
        return null
    }

    fun getNextInst(index: Int): InstNode? {
        var offset = index + 1
        while (offset < codeList.size) {
            if (codeList[offset] != null) {
                return codeList[offset]
            } else {
                offset++
            }
        }
        return null
    }

    fun getInst(index: Int): InstNode? {
        if (index < codeList.size) {
            return codeList[index]
        }
        return null
    }

    fun addExceptionHandler(exceHandler: ExceptionHandler): ExceptionHandler {
        if (!catchList.isEmpty()) {
            for (e in catchList) {
                if (e == exceHandler) {
                    return e
                }
                if (e.addr == exceHandler.addr) {
                    if (e.handlerBlock == exceHandler.handlerBlock) {
                        e.addException(exceHandler)
                    } else {
                        // merge different block
                    }
                    return e
                }
            }
        }
        catchList.add(exceHandler)
        return exceHandler
    }

    fun getReturnType() = mthInfo.retType

    override fun setValue(key: AttrKey, value: AttrValue) {
        if (key == AttrKey.ANNOTATION) {
            val addSystem = fun(anno: Annotation?) {
                if (anno == null) return
                if ((anno.visibility == Visibility.SYSTEM) && (isSystemCommentType(anno.type.getAsObjectType()!!))) {
                    sysAnnoMap[anno.type] = anno
                }
            }
            if (value is AttrValueList) {
                for (anno in value.value) {
                    addSystem(anno.getAsAnnotation())
                }
            } else {
                addSystem(value.getAsAnnotation())
            }
        } else {
            super.setValue(key, value)
        }
    }
}