package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.Code

class MethodNode(val parent: ClassNode, val mthData: ClassData.Method, val isVirtual: Boolean): AccessInfo, AttrNode {
    override val accFlags = mthData.accessFlags
    override val attributes: MutableMap<AttrKey, Any> = HashMap()

    val mthInfo = MethodInfo.fromDex(parent.parent, mthData.methodIndex)
    val descriptor = mthInfo.parseSignature()

    val noCode = mthData.codeOffset == 0

    var regsCount: Int = 0
    var codeSize: Int = 0
    var debugInfoOffset: Int = 0
    var mthCode: Code = parent.parent.dex.readCode(mthData)

    fun load() {
        try {
            if (noCode) {
                regsCount = 0
                codeSize = 0
                initMethodTypes()
                return
            }
            regsCount = mthCode.registersSize
            debugInfoOffset = mthCode.debugInfoOffset
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun initMethodTypes() {

    }
}