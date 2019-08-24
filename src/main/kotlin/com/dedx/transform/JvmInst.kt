package com.dedx.transform

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

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
        }
    }
}

class TypeInst(override val opcodes: Int, override var label: Label?, val typeString: String): JvmInst {
    override var lineNumber: Int? = null
    override fun visitInst(transformer: InstTransformer) {
        transformer.methodVisitor().visitTypeInsn(opcodes, typeString)
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
                .visitFieldInsn(jvmOpcodes.GETSTATIC, fieldInfo.declClass.className(), fieldInfo.name, fieldInfo.type.descriptor())
    }
}