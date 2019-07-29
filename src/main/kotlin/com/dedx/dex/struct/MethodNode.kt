package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.Code
import com.android.dx.io.OpcodeInfo
import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.android.dx.io.instructions.ShortArrayCodeInput
import com.dedx.utils.DecodeException
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

class MethodNode(val parent: ClassNode, val mthData: ClassData.Method, val isVirtual: Boolean): AccessInfo, AttrNode {
    override val accFlags = mthData.accessFlags
    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()

    val mthInfo = MethodInfo.fromDex(parent.parent, mthData.methodIndex)
    val descriptor = mthInfo.parseSignature()
    val argsList = ArrayList<InstArgNode>()
    var thisArg: InstArgNode? = null

    val noCode = mthData.codeOffset == 0

    var regsCount: Int = 0
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
            initMethodTypes()
            initTryCatches(mthCode!!)
            regsCount = mthCode!!.registersSize
            debugInfoOffset = mthCode!!.debugInfoOffset
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

    }

    private fun initMethodTypes() {
        if (isStatic()) {
            thisArg = InstArgNode(0, parent.clsInfo.type)
        }
        if (noCode) {
            return
        }
        var pos = 1
        for (args in mthInfo.args) {
            if (pos < regsCount) {
                throw DecodeException("regs count less argument count in $mthInfo")
            }
            argsList.add(InstArgNode(pos, args))
            pos++
        }
    }

    fun getArguments(includeThis: Boolean): List<InstArgNode> {
        if (includeThis and (thisArg != null)) {
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
}