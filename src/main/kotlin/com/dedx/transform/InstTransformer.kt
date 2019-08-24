package com.dedx.transform

import com.dedx.dex.struct.FieldInfo
import com.dedx.dex.struct.MethodInfo
import java.util.*

class InstTransformer(val mthTransformer: MethodTransformer) {
    private val jvmInstList = LinkedList<JvmInst>()

    fun pushJvmInst(jvmInst: JvmInst) = jvmInstList.add(jvmInst)

    fun removeJvmInst(jvmInst: JvmInst) = jvmInstList.remove(jvmInst)

    fun visitJvmInst() {
        for (jvmInst in jvmInstList) {
            jvmInst.visitLabel(this)
            jvmInst.visitInst(this)
        }
    }

    fun methodVisitor() = mthTransformer.mthVisit

    fun dexNode() = mthTransformer.dexNode

    fun methodInfo(mthIndex: Int) = MethodInfo.fromDex(dexNode(), mthIndex)

    fun fieldInfo(fieldIndex: Int) = FieldInfo.fromDex(dexNode(), fieldIndex)

    fun string(cIndex: Int) = dexNode().getString(cIndex)
}