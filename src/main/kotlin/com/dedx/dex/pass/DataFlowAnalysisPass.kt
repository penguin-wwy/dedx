package com.dedx.dex.pass

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.dedx.transform.BasicBlock
import com.dedx.transform.MethodTransformer
import com.dedx.utils.BitArray
import com.dedx.utils.BlockEmptyException
import com.dedx.utils.DataFlowAnalyzeException
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList

open class DataFlowMethodInfo(val mthTransformer: MethodTransformer) {
    val blockInfos = HashMap<BasicBlock, DataFlowBlockInfo>()

    init {
        for (bb in mthTransformer.blockMap.values) {
            blockInfos[bb] = DataFlowBlockInfo(bb, mthTransformer.mthNode.regsCount)
        }
    }
    open fun getBlocks() = blockInfos.keys
    open fun getBlockInfos() = blockInfos.values
    open fun getBlockInfo(block: BasicBlock) = blockInfos[block]
    open fun isExit(block: BasicBlock) = mthTransformer.exits.contains(block)
}

open class DataFlowBlockInfo(val block: BasicBlock, regCount: Int) {
    val use = BitArray(regCount)
    val def = BitArray(regCount)
    var liveIn: BitArray? = null
    var liveOut: BitArray? = null
}

object DataFlowAnalysisPass {

    fun visit(meth: MethodTransformer): DataFlowMethodInfo {
        val dfMethodInfo = DataFlowMethodInfo(meth)
        usedefAnalysis(dfMethodInfo)
        return dfMethodInfo
    }

    fun usedefAnalysis(dfMethodInfo: DataFlowMethodInfo) {
        for (entry in dfMethodInfo.getBlockInfos()) {
            DataFlowAnalysisPass.usedefAnalysis(entry)
        }
    }

    fun livenessAnalyzer(dfMethodInfo: DataFlowMethodInfo) {
        for (blockInfo in dfMethodInfo.getBlockInfos()) {
            blockInfo.liveIn = BitArray(0)
        }
        val blockReverseList = ArrayList<BasicBlock>()
        blockReverseList.addAll(dfMethodInfo.getBlocks())
        Collections.sort(blockReverseList, kotlin.Comparator { t1, t2 ->
            val c1 = t1.firstCursor() ?: throw BlockEmptyException("Compare to empty block")
            val c2 = t2.firstCursor() ?: throw BlockEmptyException("Compare to empty block")
            return@Comparator c2.compareTo(c1)
        })
        blockReverseList.removeIf { bb -> dfMethodInfo.isExit(bb) }
        val lastLiveIn = Array(blockReverseList.size) {
            i -> dfMethodInfo.getBlockInfo(blockReverseList[i])?.liveIn!!._array.copyOf()
        }
        val beChange = fun(): Boolean {
            var result = false
            for (i in 0 until blockReverseList.size) {
                val liveIn = dfMethodInfo.getBlockInfo(blockReverseList[i])?.liveIn
                if (!liveIn!!.equal(lastLiveIn[i])) {
                    result = true
                }
                lastLiveIn[i] = liveIn._array.copyOf()
            }
            return result
        }
        do {
            for (i in 0 until blockReverseList.size) {
                val block = blockReverseList[i]
                val succList = block.successor.stream().map {
                    basicBlock -> dfMethodInfo.getBlockInfo(basicBlock)?.liveIn
                        ?: throw DataFlowAnalyzeException("block's LiveIn empty")
                }.collect(Collectors.toList()).toList()
                val currBlockInfo = dfMethodInfo.getBlockInfo(block)
                        ?: throw BlockEmptyException("Computer liveness with empty block info")
                currBlockInfo.liveOut = BitArray.merge(succList)
                currBlockInfo.liveIn = BitArray.merge(currBlockInfo.use, BitArray.sub(currBlockInfo.liveOut!!, currBlockInfo.def))
            }
        } while (beChange())
    }

    private fun usedefAnalysis(blockInfo: DataFlowBlockInfo) {
        for (inst in blockInfo.block.instList) {
            val (use, def) = getUseDefReg(inst.instruction)
            for (u in use) {
                if ((blockInfo.def.get(u) == 0) && (blockInfo.use.get(u) == 0)) {
                    blockInfo.use.setTrue(u)
                }
            }
            for (d in def) {
                if ((blockInfo.use.get(d) == 0) && (blockInfo.def.get(d) == 0)) {
                    blockInfo.def.setTrue(d)
                }
            }
        }
    }

    private fun getUseDefReg(inst: DecodedInstruction): Pair<IntArray/*use*/, IntArray/*def*/> {
        when (inst.opcode) {
            in Opcodes.MOVE..Opcodes.MOVE_16,
            in Opcodes.MOVE_OBJECT..Opcodes.MOVE_OBJECT_16,
            Opcodes.INSTANCE_OF, Opcodes.ARRAY_LENGTH,
            Opcodes.NEW_ARRAY,
            Opcodes.IGET, in Opcodes.IGET_OBJECT..Opcodes.IGET_SHORT,
            Opcodes.NEG_INT, Opcodes.NOT_INT, Opcodes.NEG_FLOAT,
            Opcodes.INT_TO_FLOAT, Opcodes.FLOAT_TO_INT, Opcodes.INT_TO_BYTE, Opcodes.INT_TO_CHAR, Opcodes.INT_TO_SHORT,
            in Opcodes.ADD_INT_LIT16..Opcodes.XOR_INT_LIT16,
            in Opcodes.ADD_INT_LIT8..Opcodes.USHR_INT_LIT8 -> {
                return Pair(intArrayOf(inst.b), intArrayOf(inst.a))
            }
            Opcodes.MOVE_RESULT,
            in Opcodes.MOVE_RESULT_OBJECT..Opcodes.MOVE_EXCEPTION,
            in Opcodes.CONST_4..Opcodes.CONST_HIGH16,
            in Opcodes.CONST_STRING..Opcodes.CONST_CLASS,
            Opcodes.NEW_INSTANCE,
            in Opcodes.IF_EQZ..Opcodes.IF_LEZ,
            Opcodes.SGET, in Opcodes.SGET_OBJECT..Opcodes.SGET_SHORT,
            Opcodes.CONST_METHOD_HANDLE, Opcodes.CONST_METHOD_TYPE -> {
                return Pair(IntArray(0), intArrayOf(inst.a))
            }
            in Opcodes.CONST_WIDE_16..Opcodes.CONST_WIDE_HIGH16,
            Opcodes.SGET_WIDE -> {
                return Pair(IntArray(0), intArrayOf(inst.a, inst.a + 1))
            }
            in Opcodes.MOVE_WIDE..Opcodes.MOVE_WIDE_16,
            Opcodes.NEG_LONG, Opcodes.NOT_LONG, Opcodes.NEG_DOUBLE,
            Opcodes.LONG_TO_DOUBLE, Opcodes.DOUBLE_TO_LONG -> {
                return Pair(intArrayOf(inst.b, inst.b + 1), intArrayOf(inst.a, inst.a + 1))
            }
            in Opcodes.IF_EQ..Opcodes.IF_LE,
            Opcodes.IPUT, in Opcodes.IPUT_OBJECT..Opcodes.IPUT_SHORT,
            in Opcodes.ADD_INT_2ADDR..Opcodes.USHR_INT_2ADDR,
            in Opcodes.ADD_FLOAT_2ADDR..Opcodes.REM_FLOAT_2ADDR -> {
                return Pair(intArrayOf(inst.a, inst.b), IntArray(0))
            }
            Opcodes.RETURN, Opcodes.RETURN_OBJECT, Opcodes.MONITOR_ENTER, Opcodes.MONITOR_EXIT, Opcodes.CHECK_CAST,
            Opcodes.FILL_ARRAY_DATA, Opcodes.THROW, Opcodes.PACKED_SWITCH, Opcodes.SPARSE_SWITCH,
            Opcodes.SPUT, in Opcodes.SPUT_OBJECT..Opcodes.SPUT_SHORT -> {
                return Pair(intArrayOf(inst.a), IntArray(0))
            }
            Opcodes.RETURN_WIDE,
            Opcodes.SPUT_WIDE -> {
                return Pair(intArrayOf(inst.a, inst.a + 1), IntArray(0))
            }
            in Opcodes.CMPL_FLOAT..Opcodes.CMP_LONG -> {
                return Pair(intArrayOf(inst.b, inst.b + 1, inst.c, inst.c + 1), intArrayOf(inst.a))
            }
            Opcodes.AGET, in Opcodes.AGET_OBJECT..Opcodes.AGET_SHORT,
            in Opcodes.ADD_INT..Opcodes.USHR_INT,
            in Opcodes.ADD_FLOAT..Opcodes.REM_FLOAT -> {
                return Pair(intArrayOf(inst.b, inst.c), intArrayOf(inst.a))
            }
            in Opcodes.ADD_LONG..Opcodes.USHR_LONG,
            in Opcodes.ADD_DOUBLE..Opcodes.REM_DOUBLE -> {
                return Pair(intArrayOf(inst.b, inst.b + 1, inst.c, inst.c + 1), intArrayOf(inst.a, inst.a + 1))
            }
            Opcodes.AGET_WIDE -> {
                return Pair(intArrayOf(inst.b, inst.c), intArrayOf(inst.a, inst.a + 1))
            }
            Opcodes.APUT, in Opcodes.APUT_OBJECT..Opcodes.APUT_SHORT -> {
                return Pair(intArrayOf(inst.a, inst.b, inst.c), IntArray(0))
            }
            Opcodes.APUT_WIDE -> {
                return Pair(intArrayOf(inst.a, inst.a + 1, inst.b, inst.c), IntArray(0))
            }
            Opcodes.IGET_WIDE,
            Opcodes.INT_TO_LONG, Opcodes.INT_TO_DOUBLE, Opcodes.FLOAT_TO_LONG, Opcodes.FLOAT_TO_DOUBLE -> {
                return Pair(intArrayOf(inst.b), intArrayOf(inst.a, inst.a + 1))
            }
            Opcodes.LONG_TO_INT, Opcodes.LONG_TO_FLOAT, Opcodes.DOUBLE_TO_INT, Opcodes.DOUBLE_TO_FLOAT -> {
                return Pair(intArrayOf(inst.b, inst.b + 1), intArrayOf(inst.a))
            }
            in Opcodes.ADD_LONG_2ADDR..Opcodes.USHR_LONG_2ADDR,
            in Opcodes.ADD_DOUBLE_2ADDR..Opcodes.REM_DOUBLE_2ADDR -> {
                return Pair(intArrayOf(inst.a, inst.a + 1, inst.b, inst.b + 1), IntArray(0))
            }
            Opcodes.IPUT_WIDE -> {
                return Pair(intArrayOf(inst.a, inst.a + 1, inst.b), IntArray(0))
            }
            Opcodes.FILLED_NEW_ARRAY -> {
                // TODO
            }
            Opcodes.FILLED_NEW_ARRAY_RANGE -> {
                // TODO
            }
            in Opcodes.INVOKE_VIRTUAL..Opcodes.INVOKE_INTERFACE -> {
                // TODO
            }
            in Opcodes.INVOKE_VIRTUAL_RANGE..Opcodes.INVOKE_INTERFACE_RANGE -> {
                // TODO
            }
            Opcodes.INVOKE_POLYMORPHIC -> {
                // TODO
            }
            Opcodes.INVOKE_POLYMORPHIC_RANGE -> {
                // TODO
            }
            Opcodes.INVOKE_CUSTOM -> {
                // TODO
            }
            Opcodes.INVOKE_CUSTOM_RANGE -> {
                // TODO
            }
            else -> {
                // TODO
            }
        }
        return Pair(IntArray(0), IntArray(0))
    }
}