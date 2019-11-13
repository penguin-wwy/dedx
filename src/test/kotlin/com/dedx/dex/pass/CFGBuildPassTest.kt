package com.dedx.dex.pass

import com.dedx.dex.struct.DexNode
import com.dedx.tools.EmptyConfiguration
import com.dedx.transform.ClassTransformer
import com.dedx.transform.MethodTransformer
import org.junit.Assert.*
import org.junit.Test

class CFGBuildPassTest {
    @Test
    fun CFGBuilding() {
        val bytes = CFGBuildPassTest::class.java.getResource("/CFGTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val testClazz = dexNode.getClass("com.test.CFGTest")
        assertFalse(testClazz == null)
        val methodNode = testClazz?.searchMethodByProto("testOne", "(Ljava/lang/String;I)Ljava/lang/String;")
        assertTrue(methodNode != null)
        val transformer = MethodTransformer(methodNode!!, ClassTransformer(testClazz!!, EmptyConfiguration))
        CFGBuildPass.visit(transformer)
        assertFalse(transformer.blockMap.isEmpty())
        for (entry in transformer.blockMap) {
            for (inst in entry.value.instList) {
                print("${java.lang.Integer.toHexString(inst.cursor)} ")
            }
            println()
        }
    }
}