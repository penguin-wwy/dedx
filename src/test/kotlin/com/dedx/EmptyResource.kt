package com.dedx

import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.MethodNode
import java.lang.RuntimeException

object EmptyResource {
    val dexNode: DexNode by lazy {
        val bytes = EmptyResource::class.java.getResource("/empty.dex").openStream().readBytes()
        DexNode.create(bytes)
    }

    val classNode: ClassNode by lazy {
        dexNode.loadClass()
        dexNode.getClass("Empty") ?: throw RuntimeException("Get empty class failed.")
    }

    val emptyMethodNode: MethodNode by lazy {
        classNode.searchMethodByProto("empty", "()V") ?: throw RuntimeException("Get empty method failed.")
    }

    val staticMethodNode: MethodNode by lazy {
        classNode.searchMethodByProto("static_empty", "()V") ?: throw RuntimeException("Get static empty method failed.")
    }
}