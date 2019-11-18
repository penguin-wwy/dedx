package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.ClassDef
import com.dedx.dex.parser.AnnotationsParser
import com.dedx.dex.parser.StaticValuesParser
import com.dedx.dex.struct.type.ObjectType
import com.dedx.tools.Configuration
import com.dedx.utils.DecodeException
import com.google.common.flogger.FluentLogger
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ClassNode private constructor(val parent: DexNode,
                                    private val clsDef: ClassDef,
                                    val clsInfo: ClassInfo,
                                    clsData: ClassData?): AccessInfo, AttrNode {

    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    override val accFlags: Int = clsDef.accessFlags
    val interfaces: Array<ObjectType> = Array(clsDef.interfaces.size) {
        i -> parent.getType(clsDef.interfaces[i].toInt()).getAsObjectType() ?: throw DecodeException("Interface type error.")
    }
    val methods: List<MethodNode> = addMethods(this, clsData)
    val fields: List<FieldNode> = addFields(this, clsDef, clsData)

    private val mthCache: MutableMap<MethodInfo, MethodNode> = HashMap(methods.size)
    private val fieldCache: MutableMap<FieldInfo, FieldNode> = HashMap(fields.size)

    init {
        for (mth in methods) {
            mthCache[mth.mthInfo] = mth
        }
        for (field in fields) {
            fieldCache[field.fieldInfo] = field
        }

        val offset = clsDef.annotationsOffset
        if (offset != 0) {
            try {
                AnnotationsParser(parent, this).parse(offset)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log()
            }
        }
    }

    class ClassNodeFactory(private val configuration: Configuration) {

        private lateinit var parent: DexNode
        private lateinit var clsDef: ClassDef
        private var clsData: ClassData? = null

        fun setDexNode(parent: DexNode) = apply {
            this.parent = parent
        }

        fun setClassDef(cls: ClassDef) = apply {
            clsDef = cls
        }

        fun setClassData(clsData: ClassData?) = apply {
            this.clsData = clsData
        }

        fun create(): ClassNode? {
            val clsInfo: ClassInfo = ClassInfo.fromDex(parent, clsDef.typeIndex)
            if (configuration.blackClasses.isNotEmpty() && configuration.blackClasses.find { clsInfo.fullName.startsWith(it) } != null) {
                logger.atInfo().log("Class skip by hit black classes list [$clsInfo]")
                return null
            }
            if (configuration.classesList.isNotEmpty() && configuration.classesList.find { clsInfo.fullName.startsWith(it) } == null) {
                logger.atInfo().log("Class skip by no hit classes list [$clsInfo]")
                return null
            }
            return create(parent, clsDef, clsInfo, clsData)
        }
    }


    companion object {
        private val logger = FluentLogger.forEnclosingClass()

        fun addMethods(parent: ClassNode, clsData: ClassData?): List<MethodNode> {
            if (clsData == null) {
                return Collections.emptyList()
            }
            val mthsCount = clsData.directMethods.size + clsData.virtualMethods.size
            val methods = ArrayList<MethodNode>(mthsCount)
            for (method in clsData.directMethods) {
                methods.add(MethodNode(parent, method, false))
            }
            for (method in clsData.virtualMethods) {
                methods.add(MethodNode(parent, method, true))
            }
            return methods
        }

        fun addFields(parent: ClassNode, cls: ClassDef, clsData: ClassData?): List<FieldNode> {
            if (clsData == null) {
                return Collections.emptyList()
            }
            val fieldsCount = clsData.staticFields.size + clsData.instanceFields.size
            val fields = ArrayList<FieldNode>(fieldsCount)
            for (field in clsData.staticFields) {
                fields.add(FieldNode.create(parent, field))
            }
            loadStaticValues(parent, cls, fields)
            for (field in clsData.instanceFields) {
                fields.add(FieldNode.create(parent, field))
            }
            return fields
        }

        private fun loadStaticValues(parent: ClassNode, cls: ClassDef, staticFields: List<FieldNode>) {
            for (field in staticFields) {
                if (field.isFinal()) {
                    field.setValue(AttrKey.CONST, AttrValue(Enc.ENC_NULL, null))
                }
            }
            val offset = cls.staticValuesOffset
            if (offset == 0) {
                return
            }
            val section = parent.parent.dex.open(offset)
            StaticValuesParser(parent.parent, section).processFields(staticFields)
            ConstStorage.processConstFields(parent, staticFields)
        }

        private fun create(parent: DexNode, clsDef: ClassDef, clsInfo: ClassInfo, clsData: ClassData?)
                = ClassNode(parent, clsDef, clsInfo, clsData)
    }

    fun load() = apply { methods.forEach { it.load() } }

    fun searchField(fieldInfo: FieldInfo): FieldNode? {
        return fieldCache[fieldInfo]
    }

    fun searchFieldById(index: Int): FieldNode? {
        return searchField(FieldInfo.fromDex(parent, index))
    }

    fun searchMethod(mthInfo: MethodInfo): MethodNode? {
        return mthCache[mthInfo]
    }

    fun searchMethodById(index: Int): MethodNode? {
        return searchMethod(MethodInfo.fromDex(parent, index))
    }

    fun searchMethodByProto(name: String, proto: String): MethodNode? {
        for (entry in mthCache) {
            if ((entry.key.name == name) && (entry.key.parseSignature() == proto)) {
                return entry.value
            }
        }
        return null
    }

    fun superClassName() = clsInfo.parentClass?.className()

    fun superClassNameWithSlash() = clsInfo.parentClass?.className()?.replace('.', '/')

    fun hasSuperClass() = clsInfo.parentClass != null

    fun getSourceFile() = parent.getString(clsDef.sourceFileIndex)

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ClassNode) {
            return false
        }
        return clsInfo == other.clsInfo
    }
}