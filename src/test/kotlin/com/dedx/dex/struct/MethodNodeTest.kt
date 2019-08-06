package com.dedx.dex.struct

import org.junit.Assert.*
import org.junit.Test

class MethodNodeTest {
    @Test
    fun testLoadMethodCode() {
        val bytes = MethodNodeTest::class.java.getResource("/SingleTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val singleTestClazz = dexNode.clsMap.get(ClassInfo.fromDex("com.test.SingleTest"))
        for (method in singleTestClazz!!.methods) {
            method.load()
        }
    }

    @Test
    fun testTryCatch() {
        val bytes = MethodNodeTest::class.java.getResource("/TryCatch.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val testClazz = dexNode.getClass("TryCatch")
        val testMethod = testClazz?.searchMethodByProto("NextLineSize", "(Ljava/io/BufferedReader;)I")
        testMethod?.load()
    }
}