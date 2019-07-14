package com.dedx.dex.struct.type

class ObjectType(val typeName: String) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ObjectType) {
            return false
        }
        return typeName == other.typeName
    }
}