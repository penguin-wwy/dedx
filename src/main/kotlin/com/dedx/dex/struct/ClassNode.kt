package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.ClassDef
import com.dedx.dex.parser.AnnotationsParser
import com.dedx.dex.parser.EncValueParser
import com.dedx.dex.parser.StaticValuesParser
import com.dedx.dex.struct.type.TypeBox
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ClassNode private constructor(val parent: DexNode, val cls: ClassDef, clsData: ClassData?): AccessInfo, AttrNode {

    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    override val accFlags: Int = cls.accessFlags
    val clsInfo: ClassInfo = ClassInfo.fromDex(parent, cls.typeIndex)
    private val interfaces: Array<TypeBox> = Array(cls.interfaces.size) {
        i -> parent.getType(cls.interfaces[i].toInt())
    }
    val methods: List<MethodNode> = addMethods(this, clsData)
    val fields: List<FieldNode> = addFields(this, cls, clsData)

    private val mthCache: MutableMap<MethodInfo, MethodNode> = HashMap(methods.size)
    private val fieldCache: MutableMap<FieldInfo, FieldNode> = HashMap(fields.size)

    init {
        for (mth in methods) {
            mthCache[mth.mthInfo] = mth
        }
        for (field in fields) {
            fieldCache[field.fieldInfo] = field
        }

        val offset = cls.annotationsOffset
        if (offset != 0) {
            try {
                AnnotationsParser(parent, this).parse(offset)
            } catch (e: Exception) {
                // TODO
                e.printStackTrace()
            }
        }

        val accFlagsValue: Int = 0
    }

    companion object : ClassNodeFactory<ClassNode> {

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

        override fun create(parent: DexNode, cls: ClassDef, clsData: ClassData?) = ClassNode(parent, cls, clsData)
    }

    fun load(): ClassNode {
        for (mth in methods) {
            mth.load()
        }
        return this
    }

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
            if ((entry.key.name == name) and (entry.key.parseSignature() == proto)) {
                return entry.value
            }
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ClassNode) {
            return false
        }
        return clsInfo == other.clsInfo
    }
}