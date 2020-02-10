package com.dedx.transform

import com.dedx.dex.struct.DexNode
import org.junit.Assert.*
import org.junit.Test

class MethodTransformerTest {
    @Test
    fun singleTest() {
        val bytes = MethodTransformerTest::class.java.getResource("/SingleTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val testClazz = dexNode.getClass("com.test.SingleTest")
        val classTransformer = ClassTransformer(testClazz!!, "")
        classTransformer.visitClass()
        val path = MethodTransformerTest::class.java.getResource("/SingleTest.dex")
                .file.replace("SingleTest.dex", "com/test/SingleTest.class")
        println(path)
    }
}
