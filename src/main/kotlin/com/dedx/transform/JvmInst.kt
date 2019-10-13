package com.dedx.transform

import com.android.dx.io.instructions.FillArrayDataPayloadDecodedInstruction
import com.android.dx.io.instructions.PackedSwitchPayloadDecodedInstruction
import com.android.dx.io.instructions.SparseSwitchPayloadDecodedInstruction
import com.dedx.utils.DecodeException
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

// extended instructions that cannot be referred to
// representing Dalvik FILL_ARRAY_DATA instructions
const val FILL_ARRAY_DATA = -1
const val Packed_Switch_Payload = -2
const val Sparse_Switch_Payload = -3

class LabelInst() {
    private var value: Label? = null
    var inst: JvmInst? = null
    constructor(label: Label): this() {
        value = label
    }

    fun getValue() = value

    fun getValueOrCreate(): Label {
        if (value == null) {
            // TODO log out
            value = Label()
        }
        return value as Label
    }
}

interface JvmInst: Opcodes {
    val opcodes: Int
    var label: LabelInst
    var lineNumber: Int?

    open fun setLineNumber(lineNumber: Int?): JvmInst {
        if (lineNumber != null) {
            this.lineNumber = lineNumber
        }
        return this
    }

    open fun visitLabel(transformer: InstTransformer) {
        val label0 = label.getValue() ?: return
        val line = lineNumber ?: return
        transformer.methodVisitor().visitLabel(label0)
        transformer.methodVisitor().visitLineNumber(line, label0)
    }
    open fun visitInst(transformer: InstTransformer)

    companion object {
        fun CreateSingleInst(opcodes: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return SingleInst(opcodes, label).setLineNumber(lineNumber)
        }

        fun CreateSlotInst(opcodes: Int, slot: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return SlotInst(opcodes, label, slot).setLineNumber(lineNumber)
        }

        fun CreateIntInst(opcodes: Int, number: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return IntInst(opcodes, label, number).setLineNumber(lineNumber)
        }

        fun CreateLiteralInst(opcodes: Int, literal: Long, type: SlotType, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return LiteralInst(opcodes, label, literal, type).setLineNumber(lineNumber)
        }

        fun CreateTypeInst(opcodes: Int, type: String, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return TypeInst(opcodes, label, type).setLineNumber(lineNumber)
        }

        fun CreateConstantInst(opcodes: Int, constIndex: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return ConstantInst(opcodes, label, constIndex).setLineNumber(lineNumber)
        }

        fun CreateInvokeInst(opcodes: Int, invokeType: Int, mthIndex: Int, label: LabelInst = LabelInst(),
                             lineNumber: Int? = null): JvmInst {
            return InvokeInst(opcodes, label, invokeType, mthIndex).setLineNumber(lineNumber)
        }

        fun CreateJumpInst(opcodes: Int, target: LabelInst, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return JumpInst(opcodes, label, target).setLineNumber(lineNumber)
        }

        fun CreateFieldInst(opcodes: Int, fieldIndex: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return FieldInst(opcodes, label, fieldIndex).setLineNumber(lineNumber)
        }

        fun CreateShadowInst(opcodes: Int, literal: Long?, regs: IntArray, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return ShadowInst(opcodes, label, literal, regs).setLineNumber(lineNumber)
        }

        fun CreateFillArrayDataPayloadInst(slot: Int, target: Int, type: SlotType, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return FillArrayDataPayloadInst(FILL_ARRAY_DATA, label, slot, target, type).setLineNumber(lineNumber)
        }

        fun CreatePackedSwitchPayloadInst(target: Int, defaultLabel: LabelInst, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return PackedSwitchPayloadInst(Packed_Switch_Payload, label, target, defaultLabel).setLineNumber(lineNumber)
        }

        fun CreateSparseSwitchPayloadInst(target: Int, defaultLabel: LabelInst, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return SparseSwitchPayloadInst(Sparse_Switch_Payload, label, target, defaultLabel).setLineNumber(lineNumber)
        }

        fun CreateMultiANewArrayInsn(typeName: String, numDimensions: Int, label: LabelInst = LabelInst(), lineNumber: Int? = null): JvmInst {
            return MultiANewArrayInsn(label, typeName, numDimensions).setLineNumber(lineNumber)
        }
    }
}

abstract class JvmInst2(override var label: LabelInst): JvmInst {
    init {
        label.inst = this
    }
}

class SingleInst(override val opcodes: Int, label: LabelInst): JvmInst2(label) {
    override var lineNumber: Int? = null

    override fun visitInst(transformer: InstTransformer) {
        if (opcodes == Opcodes.NOP) return
        transformer.methodVisitor().visitInsn(opcodes)
    }
}

class SlotInst(override val opcodes: Int, label: LabelInst, val slot: Int): JvmInst2(label) {
    override var lineNumber: Int? = null

    override fun visitInst(transformer: InstTransformer) {
        when (opcodes) {
            in Opcodes.ILOAD..Opcodes.ALOAD, in Opcodes.ISTORE..Opcodes.ASTORE, Opcodes.RET -> {
                transformer.methodVisitor().visitVarInsn(opcodes, slot)
            }
        }
    }

    fun getExprType() = when (opcodes) {
        Opcodes.ILOAD, Opcodes.ISTORE -> SlotType.INT
        Opcodes.FLOAD, Opcodes.FSTORE -> SlotType.FLOAT
        Opcodes.LLOAD, Opcodes.LSTORE -> SlotType.LONG
        Opcodes.DLOAD, Opcodes.DSTORE -> SlotType.DOUBLE
        Opcodes.ALOAD, Opcodes.ASTORE -> SlotType.OBJECT
        else -> throw DecodeException("Can't get type from SlotInst")
    }

    fun isLoadInst() = if (opcodes in Opcodes.ILOAD..Opcodes.ALOAD) true else false

    fun isStoreInst() = if (opcodes in Opcodes.ISTORE..Opcodes.ASTORE) true else false
}

class IntInst(override val opcodes: Int, label: LabelInst, val number: Int): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitIntInsn(opcodes, number)
    }
}

class LiteralInst(override val opcodes: Int, label: LabelInst, val literal: Long, val type: SlotType): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        when (opcodes) {
            Opcodes.LDC -> visitLDCInst(transformer.methodVisitor())
        }
    }

    private fun visitLDCInst(mthVisitor: MethodVisitor) {
        when (type) {
            SlotType.INT -> mthVisitor.visitLdcInsn(literal.toInt())
            SlotType.FLOAT -> mthVisitor.visitLdcInsn(Float.fromBits(literal.toInt()))
            SlotType.DOUBLE -> mthVisitor.visitLdcInsn(Double.fromBits(literal))
        }
    }
}

class TypeInst(override val opcodes: Int, label: LabelInst, val typeString: String): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        when (opcodes) {
            Opcodes.LDC -> mthVisitor.visitLdcInsn(Type.getType(typeString))
            else -> mthVisitor.visitTypeInsn(opcodes, typeString)
        }
    }
}

class ConstantInst(override val opcodes: Int, label: LabelInst, val constIndex: Int): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        when (opcodes) {
            Opcodes.LDC -> mthVisitor.visitLdcInsn(transformer.string(constIndex))
        }
    }
}

class InvokeInst(override val opcodes: Int, label: LabelInst, val invokeType: Int, val mthIndex: Int): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthInfo = transformer.methodInfo(mthIndex)
        var isInterface = false
        if (invokeType == Opcodes.INVOKEINTERFACE) {
            isInterface = true
        }
        transformer.methodVisitor()
                .visitMethodInsn(invokeType, mthInfo.declClass.className(), mthInfo.name, mthInfo.parseSignature(), isInterface)
    }
}

class JumpInst(override val opcodes: Int, label: LabelInst, var target: LabelInst): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitJumpInsn(opcodes, target.getValueOrCreate())
    }
}

class FieldInst(override val opcodes: Int, label: LabelInst, val fieldIndex: Int): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val fieldInfo = transformer.fieldInfo(fieldIndex)
        transformer.methodVisitor()
                .visitFieldInsn(opcodes, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
    }
}

class FillArrayDataPayloadInst(override val opcodes: Int, label: LabelInst, val slot: Int,
                               val target: Int, val type: SlotType): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        mthVisitor.visitVarInsn(Opcodes.ALOAD, slot)
        val payload = transformer.mthTransformer.code(target)!!.instruction as FillArrayDataPayloadDecodedInstruction
        for (i in 0 until payload.size - 1) {
            mthVisitor.visitInsn(Opcodes.DUP)
            putInst(mthVisitor, payload, i)
        }
        putInst(mthVisitor, payload, payload.size - 1)
    }
    
    private fun putInst(mthVisitor: MethodVisitor, payload: FillArrayDataPayloadDecodedInstruction, i: Int) {
        when (i) {
            in 0..5 -> mthVisitor.visitInsn(Opcodes.ICONST_0 + i)
            in 6..Byte.MAX_VALUE -> mthVisitor.visitIntInsn(Opcodes.BIPUSH, i)
            in Byte.MAX_VALUE + 1..Short.MAX_VALUE -> mthVisitor.visitIntInsn(Opcodes.SIPUSH, i)
            else -> mthVisitor.visitLdcInsn(i)
        }
        when (payload.elementWidthUnit.toInt()) {
            1 -> {
                if (type == SlotType.BYTE) {
                    mthVisitor.visitIntInsn(Opcodes.BIPUSH, (payload.data as ByteArray)[i].toInt())
                    mthVisitor.visitInsn(Opcodes.BASTORE)
                } else if (type == SlotType.CHAR) {
                    mthVisitor.visitIntInsn(Opcodes.BIPUSH, (payload.data as ByteArray)[i].toInt())
                    mthVisitor.visitInsn(Opcodes.CASTORE)
                } else {
                    throw DecodeException("")
                }
            }
            2 -> {
                if (type == SlotType.SHORT) {
                    mthVisitor.visitIntInsn(Opcodes.SIPUSH, (payload.data as ShortArray)[i].toInt())
                    mthVisitor.visitInsn(Opcodes.SASTORE)
                } else {
                    throw DecodeException("")
                }
            }
            4 -> {
                when (type) {
                    SlotType.INT -> {
                        mthVisitor.visitLdcInsn((payload.data as IntArray)[i])
                        mthVisitor.visitInsn(Opcodes.IASTORE)
                    }
                    SlotType.FLOAT -> {
                        mthVisitor.visitLdcInsn(Float.fromBits((payload.data as IntArray)[i]))
                        mthVisitor.visitInsn(Opcodes.FASTORE)
                    }
                    SlotType.OBJECT -> {
                        // TODO
                        mthVisitor.visitInsn(Opcodes.AASTORE)
                    }
                    else -> throw DecodeException("")
                }
            }
            8 -> {
                if (type == SlotType.LONG) {
                    mthVisitor.visitLdcInsn((payload.data as LongArray)[i])
                    mthVisitor.visitInsn(Opcodes.LASTORE)
                } else if (type == SlotType.DOUBLE) {
                    mthVisitor.visitLdcInsn(Double.fromBits((payload.data as LongArray)[i]))
                    mthVisitor.visitInsn(Opcodes.DASTORE)
                } else {
                    throw DecodeException("")
                }
            }
        }
    }
}

/*
* lookupswitch and tableswitch instruction need StackMapTable,
* otherwise throw java.lang.VerifyError: Expecting a stackmap frame
*/

class PackedSwitchPayloadInst(override val opcodes: Int, label: LabelInst,
                              val target: Int, val defaultLabel: LabelInst): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        val payload = transformer.mthTransformer.code(target)!!.instruction as PackedSwitchPayloadDecodedInstruction
        val labelArray = Array(payload.targets.size) {
            i -> transformer.mthTransformer.code(payload.targets[i] - target)!!.getLabelOrPut()!!.value
        }
        mthVisitor.visitTableSwitchInsn(payload.firstKey, payload.firstKey + labelArray.size - 1, defaultLabel.getValue() ?: return, *labelArray)
    }
}

class SparseSwitchPayloadInst(override val opcodes: Int, label: LabelInst,
                              val target: Int, val defaultLabel: LabelInst): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        val payload = transformer.mthTransformer.code(target)!!.instruction as SparseSwitchPayloadDecodedInstruction
        val caseArray = IntArray(payload.keys.size) {
            i -> payload.keys[i]
        }
        val labelArray = Array(payload.targets.size) {
            i -> transformer.mthTransformer.code(payload.targets[i] - target)!!.getLabelOrPut()!!.value
        }
        mthVisitor.visitLookupSwitchInsn(defaultLabel.getValue() ?: return, caseArray, labelArray)
    }
}

class MultiANewArrayInsn(label: LabelInst, var typeName: String, val numDimensions: Int): JvmInst2(label) {
    override val opcodes: Int = Opcodes.MULTIANEWARRAY
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitMultiANewArrayInsn(typeName, numDimensions)
    }
}

class ShadowInst(override val opcodes: Int, label: LabelInst, val literal: Long?, val regs: IntArray): JvmInst2(label) {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {}

    var mainInst: ShadowInst? = null
    var salves = ArrayList<ShadowInst>()

    fun isMain() = mainInst == null
    fun isSalve() = mainInst != null

    fun addSlaveInst(inst: ShadowInst): ShadowInst {
        salves.add(inst)
        return this
    }

    fun convert(type: SlotType) = when (opcodes) {
        Opcodes.LALOAD -> {
            when (type) {
                SlotType.LONG -> JvmInst.CreateSingleInst(Opcodes.LALOAD, label, lineNumber)
                SlotType.DOUBLE -> JvmInst.CreateSingleInst(Opcodes.DALOAD, label, lineNumber)
                else -> throw DecodeException("ShadowInst convert error")
            }
        }
        Opcodes.LASTORE -> {
            when (type) {
                SlotType.LONG -> JvmInst.CreateSingleInst(Opcodes.LASTORE, label, lineNumber)
                SlotType.DOUBLE -> JvmInst.CreateSingleInst(Opcodes.DASTORE, label, lineNumber)
                else -> throw DecodeException("ShadowInst convert error")
            }
        }
        Opcodes.LLOAD -> {
            when (type) {
                SlotType.LONG -> JvmInst.CreateSlotInst(Opcodes.LLOAD, regs[0], label, lineNumber)
                SlotType.DOUBLE -> JvmInst.CreateSlotInst(Opcodes.DLOAD, regs[0], label, lineNumber)
                else -> throw DecodeException("ShadowInst convert error")
            }
        }
        Opcodes.LSTORE -> {
            when (type) {
                SlotType.LONG -> JvmInst.CreateSlotInst(Opcodes.LSTORE, regs[0], label, lineNumber)
                SlotType.DOUBLE -> JvmInst.CreateSlotInst(Opcodes.DSTORE, regs[0], label, lineNumber)
                else -> throw DecodeException("ShadowInst convert error")
            }
        }
        else -> throw DecodeException("ShadowInst convert error")
    }

}