package com.dedx.dex.struct.type

class ArrayType(val subType: TypeBox) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ArrayType) {
            return false
        }
        return subType == other.subType
    }

    fun descriptor() = "[${subType.descriptor()}"

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return "$subType[]"
    }
}