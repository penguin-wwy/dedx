package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.Dex
import com.dedx.dex.struct.type.TypeBox
import com.dedx.tools.Configuration
import com.dedx.tools.EmptyConfiguration
import com.google.common.flogger.FluentLogger
import java.io.File

class DexNode private constructor(val dex: Dex, private val config: Configuration) {

    val classes = ArrayList<ClassNode>()
    val clsMap = HashMap<ClassInfo, ClassNode>()

    companion object {
        private final val logger = FluentLogger.forEnclosingClass()
        val NO_INDEX: Int = -1
        @JvmStatic fun create(filePath: String, config: Configuration = EmptyConfiguration): DexNode? {
            val dexFile = File(filePath)
            if (dexFile.exists() && dexFile.isFile()) {
                val dex = Dex(dexFile)
                return DexNode(dex, config)
            }
            return null
        }

        @JvmStatic fun create(bytes: ByteArray, config: Configuration = EmptyConfiguration)
                = DexNode(Dex(bytes), config)
    }

    fun loadClass() {
        InfoStorage.clear()
        for (clsDef in dex.classDefs()) {
            var clsData: ClassData? = null
            if (clsDef.classDataOffset != 0) {
                clsData = dex.readClassData(clsDef)
            }
            val clsNode = ClassNode.ClassNodeFactory(config)
                    .setDexNode(this)
                    .setClassDef(clsDef)
                    .setClassData(clsData)
                    .create()
                    ?.load() ?: continue
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