package com.dedx.dex.parser

import com.dedx.dex.struct.InstArgNode
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.LocalVarNode
import com.dedx.dex.struct.MethodNode


class DebugInfoParser(val mth: MethodNode, val insnList: Array<InstNode>, debugOffset: Int) {
    val dex = mth.dex()
    val section = dex.openSection(debugOffset)

    val locals = arrayOfNulls<LocalVarNode>(mth.regsCount)
    val activeRegisters = arrayOfNulls<InstArgNode>(mth.regsCount)

    fun process() {
        var addr: Int = 0
        var line = section.readUleb128()

        val paramsCount = section.readUleb128()
    }
}