package com.dedx.transform

import com.dedx.dex.struct.FieldInfo
import com.dedx.dex.struct.MethodInfo
import com.dedx.tools.Configuration
import com.dedx.utils.DecodeException
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class JumpTarget {
    var thenPos: Int? = null
    var elsePos: Int? = null
    val execPos = ArrayList<Int>()

    fun setThen(pos: Int): JumpTarget {
        thenPos = pos
        return this
    }

    fun setElse(pos: Int): JumpTarget {
        elsePos = pos
        return this
    }

    fun addExec(pos: Int): JumpTarget {
        execPos.add(pos)
        return this
    }
}

class InstTransformer(val mthTransformer: MethodTransformer) {
    private val jvmInstList = LinkedList<JvmInst>()

    private val jumpMap = HashMap<Int, JumpTarget>()
    private val labelMap = HashMap<Label, Int>()
    private val shadowInsts = HashSet<Int>()

    fun pushJvmInst(jvmInst: JvmInst) {
        if (jvmInst.label != null) {
            labelMap[jvmInst.label!!] = jvmInstList.size
        }
        if (jvmInst is ShadowInst) {
            shadowInsts.add(jvmInstList.size)
        }
        jvmInstList.add(jvmInst)
    }

    fun removeJvmInst(jvmInst: JvmInst) = jvmInstList.remove(jvmInst)

    fun visitJvmInst() {
        eliminateShadowInst()
        if (Configuration.optLevel == Configuration.NormalOpt) {
            // TODO
        }
        for (jvmInst in jvmInstList) {
            jvmInst.visitLabel(this)
            jvmInst.visitInst(this)
        }
    }

    fun methodVisitor() = mthTransformer.mthVisit

    fun dexNode() = mthTransformer.dexNode

    fun methodInfo(mthIndex: Int) = MethodInfo.fromDex(dexNode(), mthIndex)

    fun fieldInfo(fieldIndex: Int) = FieldInfo.fromDex(dexNode(), fieldIndex)

    fun string(cIndex: Int) = dexNode().getString(cIndex)

    private fun buildJumpPhase() {
        for (i in 0 until jvmInstList.size) {
            if (jvmInstList[i] is JumpInst) {
                val jumpInst = jvmInstList[i] as JumpInst
                when (jumpInst.opcodes) {
                    in Opcodes.IFEQ..Opcodes.IF_ACMPNE, Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                        jumpMap[i] = JumpTarget().setThen(i + 1).setElse(labelMap[jumpInst.label]
                                ?: throw DecodeException(""))
                    }
                    Opcodes.GOTO -> {
                        jumpMap[i] = JumpTarget().setThen(labelMap[jumpInst.label]
                                ?: throw DecodeException(""))
                    }
                }
            }
            if (jvmInstList[i] is InvokeInst) {
                // TODO set jump to exception handle block
            }
        }
    }

    // TODO if `aget` instruction, use forward traversal, else if like `aput`, use backward traversal(to do)
    private fun typeWithExecute(pos: Int, vararg slots: Int): SlotType? {
        val posStack = Stack<Int>()
        posStack.push(pos)
        // DFS algorithm
        // TODO if input slot is empty, means value is stack(jvm) top, so must get type instruction which contain type info
        while (!posStack.empty()) {
            var runPos = posStack.pop() + 1
            loop@ while (runPos < jvmInstList.size) {
                val inst = jvmInstList[runPos]
                if (inst is ShadowInst) {
                    // TODO
                }
                if (inst is SlotInst) {
                    if (inst.slot in slots) {
                        return inst.getExprType()
                    }
                }
                when (inst.opcodes) {
                    in Opcodes.IRETURN..Opcodes.RETURN, Opcodes.ATHROW -> break@loop
                    in Opcodes.IFEQ..Opcodes.IF_ACMPNE -> {
                        posStack.push(jumpMap[runPos]?.thenPos)
                        posStack.push(jumpMap[runPos]?.elsePos)
                    }
                    in Opcodes.INVOKEVIRTUAL..Opcodes.INVOKEDYNAMIC -> {
                        // TODO
                    }
                    Opcodes.GOTO -> posStack.push(jumpMap[runPos]?.thenPos)
                    else -> runPos++
                }
            }
        }
        return null
    }

    private fun eliminateShadowInst() {
        buildJumpPhase()

        for (i in shadowInsts) {
            val shadowInst = jvmInstList[i] as ShadowInst
            if (shadowInst.isSalve()) {
                continue
            }
            val type = typeWithExecute(i) ?: throw DecodeException("get ShadowInst type error")
            for (salve in shadowInst.salves) {
                val index = jvmInstList.indexOf(salve)
                if (index >= 0 && index < jvmInstList.size) {
                    jvmInstList[index] = salve.convert(type)
                }
            }
            jvmInstList[i] = shadowInst.convert(type)
        }
    }
}