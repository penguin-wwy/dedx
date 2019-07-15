package com.dedx.dex.struct

import com.android.dex.Dex
import com.dedx.dex.struct.type.TypeBox
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

    fun getType(index: Int) = TypeBox.create(getString(index))

    fun getTypeList(offset: Int): List<TypeBox> {
        val paramList = dex.readTypeList(offset)
        val results = ArrayList<TypeBox>(paramList.types.size)
        for (i in paramList.types) {
            results.add(getType(i.toInt()))
        }
        return results
    }
}