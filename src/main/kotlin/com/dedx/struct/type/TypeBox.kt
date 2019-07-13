package com.dedx.struct.type

import java.lang.RuntimeException

class TypeBox private constructor(val type: Any) {

    companion object {
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
}