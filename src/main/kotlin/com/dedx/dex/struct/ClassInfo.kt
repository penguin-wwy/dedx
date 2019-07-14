package com.dedx.dex.struct

import com.dedx.dex.struct.type.ObjectType
import com.dedx.dex.struct.type.TypeBox

class ClassInfo private constructor(val type: TypeBox,
                                    val pkg: String,
                                    val name: String,
                                    val fullName: String,
                                    val parentClass: ClassInfo?,
                                    val isInner: Boolean){

    companion object {
        fun fromType(type: TypeBox): ClassInfo {
            return InfoStorage.classes.getOrPut(type) {
                val fullName = type.getAsObjectType()?.typeName
                val dot = fullName!!.lastIndexOf('.')
                val pkg: String
                var name: String
                if (dot == -1) {
                    pkg = ""
                    name = fullName!!
                } else {
                    pkg = fullName!!.substring(0, dot)
                    name = fullName.substring(dot + 1)
                }
                val sep = fullName.lastIndexOf('$')
                var parentClass: ClassInfo? = null
                if (sep > 0 && sep != fullName.length - 1) {
                    val parentName: String = fullName.substring(0, sep)
                    parentClass = InfoStorage.classes[TypeBox.create(ObjectType(parentName))]
                    if (parentClass != null) {
                        name = fullName.substring(sep + 1)
                    }
                }
                return@getOrPut ClassInfo(type, pkg, name, fullName, parentClass, parentClass != null)
            }
        }

        fun fromDex(dex: DexNode, typeIndex: Int) = fromType(TypeBox.create(dex.getString(typeIndex)))
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ClassInfo) {
            return false
        }
        return this.type == other.type
    }

    override fun toString(): String {
        return fullName
    }
}