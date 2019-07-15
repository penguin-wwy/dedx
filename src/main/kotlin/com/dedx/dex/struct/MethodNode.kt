package com.dedx.dex.struct

import com.android.dex.ClassData

class MethodNode(val parent: ClassNode, val mthData: ClassData.Method, val isVirtual: Boolean): AccessInfo, AttrNode {
    override val accFlags = mthData.accessFlags
    override val attributes: MutableMap<AttrKey, Any> = HashMap()

    val mthInfo = MethodInfo.fromDex(parent.parent, mthData.methodIndex)

    val noCode = mthData.codeOffset == 0
}