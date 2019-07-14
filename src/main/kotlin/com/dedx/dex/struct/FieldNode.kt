package com.dedx.dex.struct

import com.android.dex.ClassData
import com.dedx.dex.struct.type.TypeBox

interface FieldFactory {
    fun create(parent: ClassNode, field: ClassData.Field): FieldNode
}

class FieldNode private constructor(val parent: ClassNode, val type: TypeBox, val name: String, access: Int): AccessInfo, AttrNode {
    override val accFlags: Int = access
    override var attributes: MutableMap<AttrKey, Any> = HashMap()
    companion object : FieldFactory {
        override fun create(parent: ClassNode, field: ClassData.Field): FieldNode {
            val fieldId = parent.parent.getFieldId(field.fieldIndex)
            val type = TypeBox.create(parent.parent.getString(fieldId.typeIndex))
            val name = parent.parent.getString(fieldId.nameIndex)
            val accessFlag = field.accessFlags
            return FieldNode(parent, type, name, accessFlag)
        }
    }
}