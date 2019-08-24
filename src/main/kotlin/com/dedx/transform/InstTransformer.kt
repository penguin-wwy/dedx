package com.dedx.transform

import java.util.*

class InstTransformer(val mthTransformer: MethodTransformer) {
    val jvmInstList = LinkedList<JvmInst>()

    fun pushJvmInst(jvmInst: JvmInst) = jvmInstList.add(jvmInst)

    fun removeJvmInst(jvmInst: JvmInst) = jvmInstList.remove(jvmInst)

    fun methodVisitor() = mthTransformer.mthVisit
}