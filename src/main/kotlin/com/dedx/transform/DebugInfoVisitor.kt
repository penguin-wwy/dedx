package com.dedx.transform

import com.android.dx.io.instructions.DecodedInstruction
import com.dedx.dex.struct.MethodNode
import java.util.*

class MethodDebugInfoVisitor(mthNode: MethodNode) {

    private var insnArr = Collections.emptyList<DecodedInstruction>()

    fun visitMethod(mthNode: MethodNode) {
        if (mthNode.debugInfoOffset == 0) {
            return
        }
//        insnArr = mthNode.codeList
        val section = mthNode.dex().dex.open(mthNode.debugInfoOffset)
    }
}