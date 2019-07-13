package com.dedx.struct

import com.android.dex.Dex
import java.io.File

class DexNode private constructor(val dex: Dex) {

    companion object : DexNodeFactory<DexNode> {
        override fun create(filePath: String): DexNode? {
            val dexFile = File(filePath)
            if (dexFile.exists() && dexFile.isFile()) {
                val dex = Dex(dexFile)
                return DexNode(dex)
            } else {
                return null
            }
        }
    }

    fun loadClass() {

    }

    fun getString(index: Int) = dex.strings()[index]

    fun getMethodId(index: Int) = dex.methodIds()[index]

    fun getFieldId(index: Int) = dex.fieldIds()[index]

    fun getProtoId(index: Int) = dex.protoIds()[index]
}