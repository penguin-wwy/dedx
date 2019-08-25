package com.dedx.transform

import com.dedx.utils.DecodeException
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

interface JvmInst: Opcodes {
    val opcodes: Int
    var label: Label?
    var lineNumber: Int?

    open fun setLineNumber(lineNumber: Int?): JvmInst {
        if (lineNumber != null) {
            this.lineNumber = lineNumber
        }
        return this
    }

    open fun visitLabel(transformer: InstTransformer) {
        val label0 = label ?: return
        val line = lineNumber ?: return
        transformer.methodVisitor().visitLabel(label0)
        transformer.methodVisitor().visitLineNumber(line, label0)
    }
    open fun visitInst(transformer: InstTransformer)

    companion object {
        fun CreateSingleInst(opcodes: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return SingleInst(opcodes, label).setLineNumber(lineNumber)
        }

        fun CreateSlotInst(opcodes: Int, slot: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return SlotInst(opcodes, label, slot).setLineNumber(lineNumber)
        }

        fun CreateIntInst(opcodes: Int, number: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return IntInst(opcodes, label, number).setLineNumber(lineNumber)
        }

        fun CreateLiteralInst(opcodes: Int, literal: Long, type: SlotType, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return LiteralInst(opcodes, label, literal, type).setLineNumber(lineNumber)
        }

        fun CreateTypeInst(opcodes: Int, type: String, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return TypeInst(opcodes, label, type).setLineNumber(lineNumber)
        }

        fun CreateConstantInst(opcodes: Int, constIndex: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return ConstantInst(opcodes, label, constIndex).setLineNumber(lineNumber)
        }

        fun CreateInvokeInst(opcodes: Int, invokeType: Int, mthIndex: Int, label: Label? = null,
                             lineNumber: Int? = null): JvmInst {
            return InvokeInst(opcodes, label, invokeType, mthIndex).setLineNumber(lineNumber)
        }

        fun CreateJumpInst(opcodes: Int, target: Label, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return JumpInst(opcodes, label, target).setLineNumber(lineNumber)
        }

        fun CreateFieldInst(opcodes: Int, fieldIndex: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return FieldInst(opcodes, label, fieldIndex).setLineNumber(lineNumber)
        }

        fun CreateShadowInst(opcodes: Int, literal: Long?, regs: IntArray, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return ShadowInst(opcodes, label, literal, regs).setLineNumber(lineNumber)
        }
    }
}

class SingleInst(override val opcodes: Int, override var label: Label?): JvmInst {
    override var lineNumber: Int? = null

    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitInsn(opcodes)
    }
}

class SlotInst(override val opcodes: Int, override var label: Label?, val slot: Int): JvmInst {
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
}

class IntInst(override val opcodes: Int, override var label: Label?, val number: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitIntInsn(opcodes, number)
    }
}

class LiteralInst(override val opcodes: Int, override var label: Label?, val literal: Long, val type: SlotType): JvmInst {
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

class TypeInst(override val opcodes: Int, override var label: Label?, val typeString: String): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        when (opcodes) {
            Opcodes.LDC -> mthVisitor.visitLdcInsn(Type.getType(typeString))
            else -> mthVisitor.visitTypeInsn(opcodes, typeString)
        }
    }
}

class ConstantInst(override val opcodes: Int, override var label: Label?, val constIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthVisitor = transformer.methodVisitor()
        when (opcodes) {
            Opcodes.LDC -> mthVisitor.visitLdcInsn(transformer.string(constIndex))
        }
    }
}

class InvokeInst(override val opcodes: Int, override var label: Label?, val invokeType: Int, val mthIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val mthInfo = transformer.methodInfo(mthIndex)
        transformer.methodVisitor()
                .visitMethodInsn(invokeType, mthInfo.declClass.className(), mthInfo.name, mthInfo.parseSignature(), false)
    }
}

class JumpInst(override val opcodes: Int, override var label: Label?, val target: Label): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitJumpInsn(opcodes, target)
    }
}

class FieldInst(override val opcodes: Int, override var label: Label?, val fieldIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        val fieldInfo = transformer.fieldInfo(fieldIndex)
        transformer.methodVisitor()
                .visitFieldInsn(opcodes, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
    }
}

class ShadowInst(override val opcodes: Int, override var label: Label?, val literal: Long?, val regs: IntArray): JvmInst {
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