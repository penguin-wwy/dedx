/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.transform

import com.android.dx.io.Opcodes
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.MethodNode
import com.dedx.dex.struct.type.BasicType
import com.dedx.dex.struct.type.TypeBox
import com.dedx.utils.DecodeException
import java.util.TreeSet

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

    companion object {
        private val InstFrames = HashMap<Int, StackFrame>()

        fun getFrameOrPut(index: Int) = InstFrames.getOrPut(index) {
            return@getOrPut StackFrame(index)
        }

        fun getFrameOrExcept(index: Int) = InstFrames.get(index) ?: throw DecodeException("No frame in $index")

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

    private val symbolTable = HashMap<Int, SymbolInfo>()
    val preFrames = TreeSet<Int>()

    fun init() = apply {
        symbolTable.clear()
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
        for (entry in other.symbolTable) {
            if (symbolTable[entry.key] == null) {
                symbolTable[entry.key] = entry.value
            } else {
                val value = symbolTable[entry.key]
                if (value != entry.value) {
                    // TODO type confliction
                    break
                }
            }
        }
    }

    fun pushElement(index: Int, type: TypeBox) {
        setSlot(index, SlotType.convert(type)!!)
    }

    fun setSlot(index: Int, type: SlotType) {
        symbolTable[index] = SymbolInfo.create(type, SymIdentifier.SymbolType)
    }

    fun setSlotArray(index: Int, vararg types: SlotType) {
        symbolTable[index] = SymbolArrayInfo(types)
    }

    fun setSlotLiteral(index: Int, literal: Long, whichType: SymIdentifier) {
        symbolTable[index] = SymbolInfo.create(literal, whichType)
    }

    fun setSlotWide(index: Int, type: SlotType) {
        symbolTable[index] = SymbolInfo.create(type, SymIdentifier.SymbolType)
        symbolTable[index + 1] = symbolTable[index]!!
    }

    fun getSlot(index: Int) = symbolTable[index]

    fun getArrayTypeExpect(slot: Int): SymbolArrayInfo {
        val info = symbolTable[slot] ?: throw DecodeException("slot <$slot> is empty")
        if (info !is SymbolArrayInfo) {
            throw DecodeException("slot <$slot> is not array type object")
        }
        return info
    }

    fun isStringIndex(slot: Int): Boolean = symbolTable[slot]?.isStringIndex() ?: false

    fun isTypeIndex(slot: Int): Boolean = symbolTable[slot]?.isSymbolTypeIndex() ?: false

    override fun toString(): String {
        val outString = StringBuilder("{\n")
        for (entry in symbolTable) {
            outString.append("\t${entry.key} : ${entry.value}\n")
        }
        outString.append("}\n")
        return outString.toString()
    }
}
