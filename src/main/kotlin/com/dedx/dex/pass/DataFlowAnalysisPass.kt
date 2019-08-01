package com.dedx.dex.pass

import com.android.dx.io.Opcodes
import com.android.dx.io.instructions.DecodedInstruction
import com.dedx.transform.BasicBlock
import com.dedx.transform.MethodTransformer
import com.dedx.utils.BitArray

class DataFlowMethodInfo(val mthTransformer: MethodTransformer) {
    val blockInfos = HashMap<BasicBlock, DataFlowBlockInfo>()

    init {
        for (bb in mthTransformer.blockMap.values) {
            blockInfos[bb] = DataFlowBlockInfo(bb, mthTransformer.mthNode.regsCount)
        }
    }
}

class DataFlowBlockInfo(val block: BasicBlock, regCount: Int) {
    val use = BitArray(regCount)
    val def = BitArray(regCount)
}

object DataFlowAnalysisPass {

    fun visit(meth: MethodTransformer): DataFlowMethodInfo {
        val dfMethodInfo = DataFlowMethodInfo(meth)
        usedefAnalysis(dfMethodInfo)
        return dfMethodInfo
    }

    fun usedefAnalysis(dfMethodInfo: DataFlowMethodInfo) {
        for (entry in dfMethodInfo.blockInfos.values) {
            DataFlowAnalysisPass.usedefAnalysis(entry)
        }
    }

    fun livenessAnalyzer(dfMethodInfo: DataFlowMethodInfo) {

    }

    private fun usedefAnalysis(blockInfo: DataFlowBlockInfo) {
        for (inst in blockInfo.block.instList) {
            val (use, def) = getUseDefReg(inst.instruction)
            for (u in use) {
                if ((blockInfo.def.get(u) == 1) and (blockInfo.use.get(u) == 0)) {
                    blockInfo.use.setTrue(u)
                }
            }
            for (d in def) {
                if ((blockInfo.use.get(d) == 1) and (blockInfo.def.get(d) == 0)) {
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

            }
            Opcodes.FILLED_NEW_ARRAY_RANGE -> {

            }
            in Opcodes.INVOKE_VIRTUAL..Opcodes.INVOKE_INTERFACE -> {

            }
            in Opcodes.INVOKE_VIRTUAL_RANGE..Opcodes.INVOKE_INTERFACE_RANGE -> {

            }
            Opcodes.INVOKE_POLYMORPHIC -> {

            }
            Opcodes.INVOKE_POLYMORPHIC_RANGE -> {

            }
            Opcodes.INVOKE_CUSTOM -> {

            }
            Opcodes.INVOKE_CUSTOM_RANGE -> {

            }
            else -> {

            }
        }
        return Pair(IntArray(0), IntArray(0))
    }
}