package com.dedx.dex.struct.type

class ArrayType(val subType: TypeBox) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ArrayType) {
            return false
        }
        return subType == other.subType
    }

    fun descriptor(): String {
        var desc = StringBuilder("[")
        var subType = this.subType
        loop@ while (true) {
            when (subType.type) {
                ArrayType::class -> {
                    desc.append("[")
                    subType = (subType as ArrayType).subType
                }
                else -> {
                    desc.append(subType.descriptor())
                    break@loop
                }
            }
        }
        return desc.toString()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return "$subType[]"
    }
}