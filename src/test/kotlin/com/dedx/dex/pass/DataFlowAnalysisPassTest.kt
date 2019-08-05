package com.dedx.dex.pass

import com.dedx.dex.struct.DexNode
import com.dedx.transform.BasicBlock
import com.dedx.transform.ClassTransformer
import com.dedx.transform.MethodTransformer
import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.Label

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

    @Test
    fun testLiveness() {

        val bytes = DataFlowAnalysisPassTest::class.java.getResource("/Empty.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        val testClazz = dexNode.getClass("com.test.Empty")
        val methodNode = testClazz?.searchMethodByProto("<init>", "()V")
        val transformer = MethodTransformer(methodNode!!, ClassTransformer(testClazz, ""))
        val bb1 = BasicBlock(Label(), ArrayList(), ArrayList(), 1)
        val bb2 = BasicBlock(Label(), ArrayList(), ArrayList(), 2)
        val bb3 = BasicBlock(Label(), ArrayList(), ArrayList(), 3)
        val bb4 = BasicBlock(Label(), ArrayList(), ArrayList(), 4)
        val exit = BasicBlock(Label(), ArrayList(), ArrayList(), 5)

        bb1.successor.add(bb2)
        bb2.predecessor.add(bb1)
        bb2.predecessor.add(bb4)
        bb2.successor.add(bb3)
        bb2.successor.add(bb4)
        bb3.predecessor.add(bb2)
        bb3.successor.add(bb4)
        bb4.predecessor.add(bb2)
        bb4.predecessor.add(bb3)
        bb4.successor.add(bb2)
        bb4.successor.add(exit)
        exit.predecessor.add(bb4)

        val bb1Info = DataFlowBlockInfo(bb1, 8)
        val bb2Info = DataFlowBlockInfo(bb2, 8)
        val bb3Info = DataFlowBlockInfo(bb3, 8)
        val bb4Info = DataFlowBlockInfo(bb4, 8)
        val exitInfo = DataFlowBlockInfo(exit, 8)

        bb1Info.use.setTrue(0)
        bb1Info.use.setTrue(1)
        bb1Info.use.setTrue(2)
        bb1Info.def.setTrue(3)
        bb1Info.def.setTrue(4)
        bb1Info.def.setTrue(5)

        bb2Info.use.setTrue(3)
        bb2Info.use.setTrue(4)

        bb3Info.use.setTrue(6)
        bb3Info.def.setTrue(5)

        bb4Info.use.setTrue(7)
        bb4Info.def.setTrue(3)

        val blockInfoMap = HashMap<BasicBlock, DataFlowBlockInfo>()
        blockInfoMap[bb1] = bb1Info
        blockInfoMap[bb2] = bb2Info
        blockInfoMap[bb3] = bb3Info
        blockInfoMap[bb4] = bb4Info
        blockInfoMap[exit] = exitInfo
        val mthInfo = object : DataFlowMethodInfo(transformer) {
            override fun getBlocks(): MutableSet<BasicBlock> {
                return blockInfoMap.keys
            }

            override fun getBlockInfos(): MutableCollection<DataFlowBlockInfo> {
                return blockInfoMap.values
            }

            override fun getBlockInfo(block: BasicBlock): DataFlowBlockInfo? {
                return blockInfoMap[block]
            }

            override fun isExit(block: BasicBlock): Boolean {
                if (block === exit) {
                    return true
                }
                return false
            }
        }

        DataFlowAnalysisPass.livenessAnalyzer(mthInfo)

        assertEquals(bb1Info.liveOut.toString(), "{3 4 6 7}")
        assertEquals(bb1Info.liveIn.toString(), "{0 1 2 6 7}")
        assertEquals(bb2Info.liveOut.toString(), "{4 6 7}")
        assertEquals(bb2Info.liveIn.toString(), "{3 4 6 7}")
        assertEquals(bb3Info.liveOut.toString(), "{4 6 7}")
        assertEquals(bb3Info.liveIn.toString(), "{4 6 7}")
        assertEquals(bb4Info.liveOut.toString(), "{3 4 6 7}")
        assertEquals(bb4Info.liveIn.toString(), "{4 6 7}")
    }
}