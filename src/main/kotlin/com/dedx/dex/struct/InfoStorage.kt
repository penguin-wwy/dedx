package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

object InfoStorage {
    val classes: MutableMap<TypeBox, ClassInfo> = HashMap()
    val methods: MutableMap<Int, MethodInfo> = HashMap()
    val fields: MutableMap<FieldInfo, FieldInfo> = HashMap()

    fun getMethod(dex: DexNode, mthId: Int): MethodInfo? {
        return methods[mthId]
    }

    fun putMethod(dex: DexNode, mthId: Int, mth: MethodInfo): MethodInfo {
        methods[mthId] = mth
        return mth
    }

    fun clear() {
        classes.clear()
        methods.clear()
        fields.clear()
    }
}
