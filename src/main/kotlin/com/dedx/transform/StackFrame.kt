package com.dedx.transform

import com.android.dx.io.Opcodes
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodNode
import com.dedx.dex.struct.type.BasicType
import com.dedx.dex.struct.type.TypeBox
import com.dedx.utils.DecodeException
import com.dedx.utils.TypeConfliction
import java.util.*
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
        private val ConstantValue = HashMap<Int, Pair<Boolean/*is constant pool index*/, Long>>()

        fun initConstantValue() = ConstantValue.clear()

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

        fun isConstantPoolIndex(slot: Int) = ConstantValue[slot]?.first ?: false
        fun isConstantPoolLiteral(slot: Int) = !(ConstantValue[slot]?.first ?: true)
        fun getLiteral(slot: Int) = ConstantValue[slot]?.second ?: throw DecodeException("No constant value in [$slot]")
        fun setLiteral(slot: Int, isIndex: Boolean, literal: Long) {
            ConstantValue[slot] = Pair(isIndex, literal)
        }
        fun delLiteral(slot: Int) = ConstantValue.remove(slot)
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
    val preFrames = TreeSet<Int>()

    fun init(): StackFrame {
        slot2type.clear()
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
                if (slot2type[entry.key] != entry.value) throw TypeConfliction("Stack frame [$cursor] can't merge [$frame] at ${entry.key}")
            }
        }
    }

    fun setSlot(index: Int, type: SlotType) {
        slot2type[index] = type
    }

    fun setSlotWide(index: Int, type: SlotType) {
        slot2type[index] = type
        slot2type[index + 1] = type
    }

    fun getSlot(index: Int) = slot2type[index]
}