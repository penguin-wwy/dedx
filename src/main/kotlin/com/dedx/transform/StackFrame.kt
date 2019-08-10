package com.dedx.transform

import com.dedx.dex.struct.type.BasicType
import com.dedx.dex.struct.type.TypeBox
import com.dedx.utils.DecodeException
import java.util.*

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

class StackFrame {
//    val reg2slot = HashMap<Int, Int?>()
//    val slot2reg = HashMap<Int, Int?>()
//    val preFrames = ArrayList<StackFrame>()
//    val sucFrames = ArrayList<StackFrame>()

    companion object {
        val InstFrames = HashMap<Int, StackFrame>()

        fun getFrameOrPut(index: Int) = InstFrames.getOrPut(index) {
            return@getOrPut StackFrame()
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

    fun merge(): StackFrame {
        if (preFrames.isNotEmpty()) {
            return this
        }
        return this
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