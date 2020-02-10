package com.dedx.transform

import com.dedx.dex.parser.DebugInfoParser
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.LocalVarNode
import com.dedx.dex.struct.MethodNode

object MethodDebugInfoVisitor {

    fun visitMethod(mthNode: MethodNode) {
        if (mthNode.debugInfoOffset == 0) {
            return
        }
        val insnArr = mthNode.codeList
        val parser = DebugInfoParser(mthNode, insnArr, mthNode.debugInfoOffset)
        val locals = parser.process()
        attachDebugInfo(mthNode, locals, insnArr)
        attachSourceInfo(mthNode, insnArr)
    }

    fun attachDebugInfo(mthNode: MethodNode, locals: List<LocalVarNode>, insts: Array<InstNode?>) {
        // TODO
    }

    fun attachSourceInfo(mthNode: MethodNode, insts: Array<InstNode?>) {
        for (inst in insts) {
            if (inst != null) {
                val line = inst.getLineNumber()
                if (line != null) {
                    mthNode.setLineNumber(line - 1)
                }
                return
            }
        }
    }
}
