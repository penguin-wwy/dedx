package com.dedx.transform

import com.android.dx.io.Opcodes
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodNode
import com.dedx.dex.struct.type.BasicType
import com.dedx.dex.struct.type.TypeBox
import com.dedx.utils.DecodeException
import com.dedx.utils.TypeConfliction
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

enum class SlotType {
    BOOLEAN,
    CHAR,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    OBJECT,
    ARRAY;

    companion object {
        const val isValue = 1
        const val isRawBits = 2
        const val isStrIndex = 4
        const val isTypeIndex = 8

        fun convert(type: TypeBox): SlotType? {
            if (type.getAsObjectType() != null) {
                return OBJECT
            }
            if (type.getAsArrayType() != null) {
                return ARRAY
            }
            when (type.getAsBasicType()) {
                BasicType.BOOLEAN -> return BOOLEAN
                BasicType.CHAR -> return CHAR
                BasicType.BYTE -> return BYTE
                BasicType.SHORT -> return SHORT
                BasicType.INT -> return INT
                BasicType.LONG -> return LONG
                BasicType.FLOAT -> return FLOAT
                BasicType.DOUBLE -> return DOUBLE
                BasicType.VOID -> return null
                else -> throw DecodeException("Convert type $type to SlotType error.")
            }
        }
    }
}

class StackFrame(val cursor: Int) {
//    val reg2slot = HashMap<Int, Int?>()
//    val slot2reg = HashMap<Int, Int?>()
//    val preFrames = ArrayList<StackFrame>()
//    val sucFrames = ArrayList<StackFrame>()

    companion object {
        private val InstFrames = HashMap<Int, StackFrame>()

        fun getFrameOrPut(index: Int) = InstFrames.getOrPut(index) {
            return@getOrPut StackFrame(index)
        }

        fun getFrameOrExcept(index: Int) = InstFrames.get(index) ?: throw DecodeException("No frame in $index")

        fun checkType(type: SlotType, offset: Int, vararg regs: Int) {
            val frame = InstFrames[offset] ?: throw DecodeException("Empty stack frame for inst[$offset]")
            for (reg in regs) {
                val regType = frame.slot2type[reg] ?: throw DecodeException("Empty slot[$reg] for inst[$offset]")
                if ((type > SlotType.INT && type != regType) || (type <= SlotType.INT && regType > SlotType.INT)) {
                    throw TypeConfliction("Type confliction when type check: [$reg]")
                }
            }
        }

        fun initInstFrame(mthNode: MethodNode) {
            InstFrames.clear()
            var prevInst: InstNode? = null
            for (curr in mthNode.codeList) {
                if (curr == null) continue
                if (prevInst == null) {
                    getFrameOrPut(curr.cursor)
                } else {
                    getFrameOrPut(curr.cursor).preFrames.add(prevInst.cursor)
                }
                prevInst = skipJumper(curr)
            }
        }

        private fun skipJumper(instNode: InstNode) = when (instNode.instruction.opcode) {
            in Opcodes.RETURN_VOID..Opcodes.RETURN_OBJECT,
            in Opcodes.THROW..Opcodes.GOTO_32 -> null
            else -> instNode
        }
    }

    val slot2type = HashMap<Int, SlotType>()
    val constantValue = HashMap<Int, Pair<Int/*mark this is a value/index */, Long>>()
    val arrayType = HashMap<Int, ArrayList<SlotType>>()
    val preFrames = TreeSet<Int>()

    fun init(): StackFrame {
        slot2type.clear()
        constantValue.clear()
        arrayType.clear()
        return this
    }

    fun addPreFrame(index: Int) {
        preFrames.add(index)
    }

    // merge prev inst's frame
    fun merge(): StackFrame {
        if (preFrames.isNotEmpty()) {
            for (frame in preFrames) {
                merge(frame)
            }
        }
        return this
    }

    private fun merge(frame: Int) {
        val other = getFrameOrPut(frame)
        for (entry in other.slot2type) {
            if (slot2type[entry.key] == null) {
                slot2type[entry.key] = entry.value
            } else {
                if (slot2type[entry.key] != entry.value) {
                    // if type conflict, means this slot must be written in current instruction
                    break
                }
            }
        }
        for (entry in other.constantValue) {
            if (constantValue[entry.key] == null) {
                constantValue[entry.key] = entry.value
            } else {
                val value = constantValue[entry.key]
                if (value?.first != entry.value.first || value.second != entry.value.second) {
                    // TODO can merge different constant value?
                    throw TypeConfliction("Stack frame [$cursor](constant) can't merge [$frame] at ${entry.key}")
                }
            }
        }
        for (entry in other.arrayType) {
            if (arrayType[entry.key] == null) {
                arrayType[entry.key] = entry.value
            } else {
                val typeArray = arrayType[entry.key]
                if (typeArray?.size != entry.value.size || typeArray?.last() != entry.value.last()) {
                    // TODO can merge different array type?
                    throw TypeConfliction("Stack frame [$cursor](array type) can't merge [$frame] at ${entry.key}")
                }
            }
        }
    }

    fun pushElement(index: Int, type: TypeBox) {
        if (type.getAsArrayType() != null) {
            var arrayType = type.getAsArrayType()!!
            var subType = arrayType.subType
            var depth = 1
            while (subType.getAsArrayType() != null) {
                depth++
                arrayType = subType.getAsArrayType()!!
                subType = arrayType.subType
            }
            setSlotArray(index, *Array(depth + 1) { i ->
                if (i < depth) SlotType.ARRAY else SlotType.convert(subType)!!})
        } else {
            setSlot(index, SlotType.convert(type)!!)
        }
    }

    fun setSlot(index: Int, type: SlotType) {
        delLiteral(index)
        delArray(index)
        slot2type[index] = type
    }

    fun setSlotLiteral(index: Int, literal: Long, whichType: Int) {
        delSlot(index)
        delArray(index)
        constantValue[index] = Pair(whichType, literal)
    }

    fun setSlotArray(index: Int, vararg types: SlotType) {
        delSlot(index)
        delLiteral(index)
        arrayType[index] = ArrayList()
        arrayType[index]?.addAll(types)
    }

    fun setSlotWide(index: Int, type: SlotType) {
        slot2type[index] = type
        slot2type[index + 1] = type
    }

    fun slotType(index: Int): SlotType? {
        if (slot2type.containsKey(index)) {
            return slot2type[index]
        }
        if (arrayType.containsKey(index)) {
            return SlotType.OBJECT
        }
        if (constantValue.containsKey(index)) {
            return SlotType.INT
        }
        return null
    }

    fun getSlot(index: Int) = slot2type[index]

    fun isConstantPoolIndex(slot: Int): Boolean {
        val type = constantValue[slot]?.first ?: return false
        return type >= 4
    }
    fun isConstantPoolLiteral(slot: Int): Boolean {
        val type = constantValue[slot]?.first ?: return false
        return type < 4
    }
    fun isStringIndex(slot: Int): Boolean {
        val type = constantValue[slot]?.first ?: return false
        return (type and 4) != 0
    }
    fun isTypeIndex(slot: Int): Boolean {
        val type = constantValue[slot]?.first ?: return false
        return (type and 8) != 0
    }
    fun getLiteralExpect(slot: Int) = constantValue[slot]?.second ?: throw DecodeException("No constant value in [$slot]")

    fun getArrayTypeExpect(slot: Int) = arrayType[slot] ?: throw DecodeException("No array type in [$slot]")

    private fun delSlot(slot: Int) = slot2type.remove(slot)

    private fun delLiteral(slot: Int) = constantValue.remove(slot)

    private fun delArray(slot: Int) = arrayType.remove(slot)
}