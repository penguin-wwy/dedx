package com.dedx.dex.pass

import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer
import com.dedx.transform.MethodTransformer
import org.junit.Assert.*
import org.junit.Test

class DataFlowAnalysisPassTest {
    @Test
    fun testUseDef() {
        val bytes = DataFlowAnalysisPassTest::class.java.getResource("/DataFlowTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val testClazz = dexNode.getClass("com.test.DataFlowTest")
        assertFalse(testClazz == null)
        val methodNode = testClazz?.searchMethodByProto("runFirst", "(Lcom/test/DataFlowTest;IIII)I")
        assertTrue(methodNode != null)
        val transformer = MethodTransformer(methodNode!!, ClassTransformer(testClazz, ""))
        CFGBuildPass.visit(transformer)
        val mthInfo = DataFlowAnalysisPass.visit(transformer)
        for (block in transformer.blockMap.values) {
            val blockInfo = mthInfo.blockInfos[block]
            when (block.instList[0].cursor) {
                0 -> {
                    assertEquals(blockInfo!!.use._array[0], 128.toByte())
                    assertEquals(blockInfo.def._array[0], 8.toByte())
                }
                3 -> {
                    assertEquals(blockInfo!!.use._array[0], 160.toByte())
                    assertEquals(blockInfo.def._array[0], 1.toByte())
                }
                9 -> {
                    assertEquals(blockInfo!!.use._array[1], 3.toByte())
                    assertEquals(blockInfo.def._array[0], 1.toByte())
                }
                0x14 -> {
                    assertEquals(blockInfo!!.use._array[0], 161.toByte())
                    assertEquals(blockInfo!!.use._array[1], 3.toByte())
                    assertEquals(blockInfo.def._array[0], 6.toByte())
                }
            }
        }
    }
}