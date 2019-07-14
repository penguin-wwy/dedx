package com.dedx.dex.struct

import com.android.dex.ClassData
import com.android.dex.ClassDef

interface ClassNodeFactory<T> {
    fun create(parent: DexNode, cls: ClassDef, clsData: ClassData?): T
}