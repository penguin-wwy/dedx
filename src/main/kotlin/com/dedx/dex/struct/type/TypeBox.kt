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

package com.dedx.dex.struct.type

import java.lang.RuntimeException

class TypeBox private constructor(val type: Any) {

    companion object {
        fun create(type: ObjectType) = TypeBox(type)

        fun create(typeName: String) = when (typeName[0]) {
            'L' -> TypeBox(getType(BasicType.OBJECT, typeName) ?: throw RuntimeException("unknown type $typeName"))
            '[' -> TypeBox(getType(BasicType.ARRAY, typeName) ?: throw RuntimeException("unknown type $typeName"))
            else -> {
                val type = BasicType.get(typeName[0]) ?: throw RuntimeException("unknown type $typeName")
                TypeBox(type)
            }
        }

        fun cleanObjectName(obj: String): String {
            if (obj[0] == 'L' && obj[obj.length - 1] == ';') {
                return obj.substring(1, obj.length - 1).replace('/', '.')
            }
            return obj
        }

        fun getType(type: BasicType, name: String): Any? {
            if (type >= BasicType.VOID && type <= BasicType.DOUBLE) {
                return type
            }
            if (type == BasicType.OBJECT) {
                return ObjectType(cleanObjectName(name))
            }
            if (type == BasicType.ARRAY) {
                return ArrayType(create(name.substring(1)))
            }
            return null
        }
    }

    fun getAsBasicType() = when (type is BasicType) {
        true -> type
        false -> null
    }

    fun getAsObjectType() = when (type is ObjectType) {
        true -> type
        false -> null
    }

    fun getAsArrayType() = when (type is ArrayType) {
        true -> type
        false -> null
    }

    fun descriptor() = when (type::class) {
        BasicType::class -> getAsBasicType()!!.descriptor()
        ObjectType::class -> getAsObjectType()!!.descriptor()
        ArrayType::class -> getAsArrayType()!!.descriptor()
        else -> ""
    }

    fun nameWithSlash() = when (type::class) {
        BasicType::class -> descriptor()
        ObjectType::class -> getAsObjectType()!!.nameWithSlash()
        ArrayType::class -> getAsArrayType()!!.nameWithSlash()
        else -> ""
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }

    override fun toString(): String {
        return type.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is TypeBox) {
            return false
        }
        if (getAsBasicType() != null) {
            return getAsBasicType() == other.getAsBasicType()
        }
        if (getAsObjectType() != null) {
            return getAsObjectType() == other.getAsObjectType()
        }
        if (getAsArrayType() != null) {
            return getAsArrayType() == other.getAsArrayType()
        }
        return false
    }
}
