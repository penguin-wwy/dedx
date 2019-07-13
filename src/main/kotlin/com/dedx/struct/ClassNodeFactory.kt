package com.dedx.struct

import com.android.dex.ClassDef

interface ClassNodeFactory<T> {
    fun create(parent: DexNode, cls: ClassDef): T
}