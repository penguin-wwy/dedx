package com.dedx.transform

import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.MethodNode
import org.objectweb.asm.Label
import java.lang.Exception
import java.util.*

class MethodTransformer(val mthNode: MethodNode, val clsTransformer: ClassTransformer) {

    val blockMap = HashMap<Label, BasicBlock>()
    var currBlock: BasicBlock? = null

    val mthVisit = clsTransformer.classWriter.visitMethod(
            mthNode.accFlags, mthNode.mthInfo.name, mthNode.descriptor,
            null, null)

    fun visitMethod() {
        if (mthNode.noCode) {
            return
        }
        if (mthNode.debugInfoOffset != DexNode.NO_INDEX) {
            MethodDebugInfoVisitor.visitMethod(mthNode)
        }
        try {
            mthVisit.visitCode()
            val label0 = Label()
            currBlock = blockMap.getOrPut(label0) {
                visitLabel(label0)
                return@getOrPut BasicBlock.create(label0, null)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun visitLabel(label0: Label) {
        mthVisit.visitLabel(label0)
//        mthVisit.visitLineNumber()
    }
}