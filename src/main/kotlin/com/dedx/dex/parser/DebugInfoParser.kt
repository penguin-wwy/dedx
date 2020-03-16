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

package com.dedx.dex.parser

import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.InstArgNode
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.LocalVarNode
import com.dedx.dex.struct.MethodNode
import com.dedx.utils.DecodeException
import kotlin.math.min

class DebugInfoParser(val mth: MethodNode, val insnList: Array<InstNode?>, debugOffset: Int) {
    val dex = mth.dex()
    val section = dex.openSection(debugOffset)

    val locals = arrayOfNulls<LocalVarNode>(mth.regsCount)
    val activeRegisters = arrayOfNulls<InstArgNode>(mth.regsCount)

    val resultList = ArrayList<LocalVarNode>()

    companion object {
        private const val DBG_END_SEQUENCE = 0x00
        private const val DBG_ADVANCE_PC = 0x01
        private const val DBG_ADVANCE_LINE = 0x02
        private const val DBG_START_LOCAL = 0x03
        private const val DBG_START_LOCAL_EXTENDED = 0x04
        private const val DBG_END_LOCAL = 0x05
        private const val DBG_RESTART_LOCAL = 0x06
        private const val DBG_SET_PROLOGUE_END = 0x07
        private const val DBG_SET_EPILOGUE_BEGIN = 0x08
        private const val DBG_SET_FILE = 0x09
        // the smallest special opcode
        private const val DBG_FIRST_SPECIAL = 0x0a
        // the smallest line number increment
        private const val DBG_LINE_BASE = -4
        // the number of line increments represented
        private const val DBG_LINE_RANGE = 15
    }

    fun process(): List<LocalVarNode> {
        var varInfoFound = false
        var addr: Int = 0
        var line = section.readUleb128()

        val paramsCount = section.readUleb128()
        val mthArgs = mth.getArguments(false)
        for (i in 0 until paramsCount) {
            val id = section.readUleb128() - 1
            if (id != DexNode.NO_INDEX) {
                val name = dex.getString(id)
                if (i < mthArgs.size) {
                    val arg = mthArgs[i]
                    val lvar = LocalVarNode.create(arg)
                    startVar(lvar, -1)
                    varInfoFound = true
                }
            }
        }

        for (arg in mthArgs) {
            val rn = arg.regNum
            locals[rn] = LocalVarNode.create(arg)
            activeRegisters[rn] = arg
        }

        addrChange(-1, 1, line)
        setLine(addr, line)

        var c = section.readByte().toUByte().toInt() and 0xFF
        while (c != DBG_END_SEQUENCE) {
            when (c) {
                DBG_ADVANCE_PC -> {
                    val addrInc = section.readUleb128()
                    val addr = addrChange(addr, addrInc, line)
                    setLine(addr, line)
                }
                DBG_ADVANCE_LINE -> {
                    line += section.readSleb128()
                }
                DBG_START_LOCAL -> {
                    val regNum = section.readUleb128()
                    val nameId = section.readUleb128() - 1
                    val typeId = section.readUleb128() - 1
                    val variable = LocalVarNode.create(dex, regNum, nameId, typeId, DexNode.NO_INDEX)
                    startVar(variable, addr)
                    varInfoFound = true
                }
                DBG_START_LOCAL_EXTENDED -> {
                    val regNum = section.readUleb128()
                    val nameId = section.readUleb128() - 1
                    val typeId = section.readUleb128() - 1
                    val signId = section.readUleb128() - 1
                    val variable = LocalVarNode.create(dex, regNum, nameId, typeId, signId)
                    startVar(variable, addr)
                    varInfoFound = true
                }
                DBG_END_LOCAL -> {
                    val regNum = section.readUleb128()
                    val variable = locals[regNum]
                    if (variable != null) {
                        endVar(variable, addr)
                    }
                    varInfoFound = true
                }
                DBG_RESTART_LOCAL -> {
                    restartVar(section.readUleb128(), addr)
                    varInfoFound = true
                }
                DBG_SET_PROLOGUE_END -> {}
                DBG_SET_EPILOGUE_BEGIN -> {}
                DBG_SET_FILE -> {
                    val idx = section.readUleb128() - 1
                    if (idx != DexNode.NO_INDEX) {
                        val sourceFile = dex.getString(idx)
                        mth.setSourceFile(sourceFile)
                    }
                }
                else -> {
                    if (c >= DBG_FIRST_SPECIAL) {
                        val adjustedOpcode = c - DBG_FIRST_SPECIAL
                        val addrInc = adjustedOpcode / DBG_LINE_RANGE
                        addr = addrChange(addr, addrInc, line)
                        line += DBG_LINE_BASE + adjustedOpcode % DBG_LINE_RANGE
                        setLine(addr, line)
                    } else {
                        throw DecodeException("")
                    }
                }
            }
            c = section.readByte().toUByte().toInt() and 0xFF
        }

        if (varInfoFound) {
            for (variable in locals) {
                if (variable != null && !variable.isEnd) {
                    endVar(variable, mth.codeSize - 1)
                }
            }
        }
        setSourceLine(addr, insnList.size, line)
        return resultList
    }

    private fun addrChange(addr: Int, addrInc: Int, line: Int): Int {
        var newAddr = addr + addrInc
        var maxAddr = insnList.size - 1
        newAddr = min(newAddr, maxAddr)
        setSourceLine(addr, newAddr, line)
        return newAddr
    }

    private fun setSourceLine(start: Int, end: Int, line: Int) {
        for (offset in (start + 1) until end) {
            setLine(offset, line)
        }
    }

    private fun setLine(offset: Int, line: Int) {
        val inst = insnList[offset]
        inst?.setLineNumber(line)
    }

    private fun restartVar(regNum: Int, addr: Int) {
        val prev = locals[regNum]
        if (prev != null) {
            endVar(prev, addr)
            val newVar = LocalVarNode.create(regNum, prev.name, prev.type)
            startVar(newVar, addr)
        } else {
            throw DecodeException("Debug info restart failed $regNum")
        }
    }

    private fun startVar(variable: LocalVarNode, addr: Int) {
        val preVar = locals[variable.regNum]
        if (preVar != null) {
            endVar(preVar, addr)
        }
        variable.start(addr)
        locals[variable.regNum] = variable
    }

    private fun endVar(variable: LocalVarNode, addr: Int) {
        if (variable.end(addr)) {
            resultList.add(variable)
        }
    }
}
