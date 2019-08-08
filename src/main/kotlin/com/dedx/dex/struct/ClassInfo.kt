package com.dedx.dex.struct

import com.dedx.dex.struct.type.ObjectType
import com.dedx.dex.struct.type.TypeBox

class ClassInfo private constructor(val type: TypeBox,
                                    val pkg: String,
                                    val name: String,
                                    val fullName: String,
                                    val parentClass: ClassInfo?,
                                    val isInner: Boolean): Comparable<ClassInfo> {

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

        fun fromDex(dex: DexNode, typeIndex: Int) = fromType(dex.getType(typeIndex))
        fun fromDex(className: String) = fromType(TypeBox.create("L$className;"))
    }

    fun className(): String {
        return fullName.replace(".", "/")
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other is ClassInfo) {
            return this.type == other.type
        } else if (other is String) {
            val otherName: String = other
            return (fullName == otherName) or (className() == otherName)
        }
        return false
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun toString(): String {
        return fullName
    }

    override fun compareTo(other: ClassInfo): Int {
        return fullName.compareTo(other.fullName)
    }
}