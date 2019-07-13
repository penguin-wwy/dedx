package com.dedx.struct

import com.android.dex.ClassDef

class ClassNode private constructor(val parent: DexNode, val cls: ClassDef) {

    private val clsInfo: ClassInfo = ClassInfo.fromDex(parent.dex, cls.typeIndex)

    companion object : ClassNodeFactory<ClassNode> {
        override fun create(parent: DexNode, cls: ClassDef) = ClassNode(parent, cls)
    }
}