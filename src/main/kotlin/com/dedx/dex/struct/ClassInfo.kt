/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.dex.struct

import com.dedx.dex.struct.type.ObjectType
import com.dedx.dex.struct.type.TypeBox

class ClassInfo private constructor(
    val type: TypeBox,
    val pkg: String,
    val name: String,
    val fullName: String,
    val parentClass: ClassInfo?,
    val isInner: Boolean
) : Comparable<ClassInfo> {

    companion object {
        const val ROOT_CLASS_NAME = "java/lang/Object"

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
            return (fullName == otherName) || (className() == otherName)
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
