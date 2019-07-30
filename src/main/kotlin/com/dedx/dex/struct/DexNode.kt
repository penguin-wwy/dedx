package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.Dex
import com.dedx.dex.struct.type.TypeBox
import java.io.File

interface DexNodeFactory<T> {
    fun create(filePath: String): T?
    fun create(bytes: ByteArray): T?
}

class DexNode private constructor(val dex: Dex) {

    val classes = ArrayList<ClassNode>()
    val clsMap = HashMap<ClassInfo, ClassNode>()

    companion object : DexNodeFactory<DexNode> {
        final val NO_INDEX: Int = -1
        override fun create(filePath: String): DexNode? {
            val dexFile = File(filePath)
            if (dexFile.exists() && dexFile.isFile()) {
                val dex = Dex(dexFile)
                return DexNode(dex)
            } else {
                return null
            }
        }

        override fun create(bytes: ByteArray) = DexNode(Dex(bytes))
    }

    fun loadClass() {
        for (cls in dex.classDefs()) {
            var clsData: ClassData? = null
            if (cls.classDataOffset != 0) {
                clsData = dex.readClassData(cls)
            }
            val clsNode = ClassNode.create(this, cls, clsData).load()
            classes.add(clsNode)
            clsMap[clsNode.clsInfo] = clsNode
        }
    }

    fun getString(index: Int) = dex.strings()[index]

    fun getMethodId(index: Int) = dex.methodIds()[index]

    fun getFieldId(index: Int) = dex.fieldIds()[index]

    fun getProtoId(index: Int) = dex.protoIds()[index]

    fun getType(index: Int) = TypeBox.create(getString(dex.typeIds()[index]))

    fun getTypeList(offset: Int): List<TypeBox> {
        val paramList = dex.readTypeList(offset)
        val results = ArrayList<TypeBox>(paramList.types.size)
        for (i in paramList.types) {
            results.add(getType(i.toInt()))
        }
        return results
    }

    fun readCode(mth: ClassData.Method) = dex.readCode(mth)

    fun openSection(offset: Int) = dex.open(offset)

    fun getClass(name: String): ClassNode? {
        for (entry in clsMap) {
            if (entry.key.equals(name)) {
                return entry.value
            }
        }
        return null
    }
}