package com.dedx.dex.struct

import com.android.dex.ClassData
import com.dedx.dex.struct.type.TypeBox

interface FieldFactory {
    fun create(parent: ClassNode, field: ClassData.Field): FieldNode
}

class FieldNode private constructor(val parent: ClassNode, val fieldInfo: FieldInfo, access: Int): AccessInfo, AttrNode {
    override val accFlags: Int = access
    override var attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    companion object : FieldFactory {
        override fun create(parent: ClassNode, field: ClassData.Field): FieldNode {
            val accessFlag = field.accessFlags
            return FieldNode(parent, FieldInfo.fromDex(parent.parent, field.fieldIndex), accessFlag)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FieldNode) {
            return false
        }
        return fieldInfo == other.fieldInfo
    }
}