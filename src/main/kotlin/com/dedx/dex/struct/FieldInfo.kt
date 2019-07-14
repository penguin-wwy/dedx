package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class FieldInfo(val declClass: ClassInfo, val name: String, val type: TypeBox) {

    companion object {
        fun from(dex: DexNode, declClass: ClassInfo, name: String, type: TypeBox): FieldInfo {
            return FieldInfo(declClass, name, type)
        }

        fun fromDex(dex: DexNode, index: Int): FieldInfo {
            val field = dex.getFieldId(index)
            return from(dex,
                    ClassInfo.fromDex(dex, field.declaringClassIndex),
                    dex.getString(field.nameIndex),
                    TypeBox.create(dex.getString(field.typeIndex)))
        }
    }
}