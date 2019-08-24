package com.dedx.transform

import org.objectweb.asm.Label
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

    open fun visit(transformer: InstTransformer)

    companion object {
        fun CreateSingleInst(opcodes: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return SingleInst(opcodes, label).setLineNumber(lineNumber)
        }

        fun CreateSlotInst(opcodes: Int, slot: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return SlotInst(opcodes, label, slot).setLineNumber(lineNumber)
        }

        fun CreateLiteralInst(opcodes: Int, literal: Long, type: SlotType, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return LiteralInst(opcodes, label, literal, type).setLineNumber(lineNumber)
        }

        fun CreateTypeInst(opcodes: Int, typeIndex: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return TypeInst(opcodes, label, typeIndex).setLineNumber(lineNumber)
        }

        fun CreateConstantInst(opcodes: Int, constIndex: Int, label: Label? = null, lineNumber: Int? = null): JvmInst {
            return ConstantInst(opcodes, label, constIndex).setLineNumber(lineNumber)
        }

        fun CreateInvokeInst(opcodes: Int, invokeType: InvokeType, mthIndex: Int, label: Label? = null,
                             lineNumber: Int? = null): JvmInst {
            return InvokeInst(opcodes, label, invokeType, mthIndex).setLineNumber(lineNumber)
        }
    }
}

class SingleInst(override val opcodes: Int, override var label: Label?): JvmInst {
    override var lineNumber: Int? = null

    override fun visit(transformer: InstTransformer) {
        transformer.methodVisitor().visitInsn(opcodes)
    }
}

class SlotInst(override val opcodes: Int, override var label: Label?, val slot: Int): JvmInst {
    override var lineNumber: Int? = null

    override fun visit(transformer: InstTransformer) {
        when (opcodes) {
            in Opcodes.ILOAD..Opcodes.ALOAD, in Opcodes.ISTORE..Opcodes.ASTORE, Opcodes.RET -> {
                transformer.methodVisitor().visitVarInsn(opcodes, slot)
            }
        }
    }
}

class LiteralInst(override val opcodes: Int, override var label: Label?, val literal: Long, val type: SlotType): JvmInst {
    override var lineNumber: Int? = null
    override fun visit(transformer: InstTransformer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class TypeInst(override val opcodes: Int, override var label: Label?, val typeIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visit(transformer: InstTransformer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class ConstantInst(override val opcodes: Int, override var label: Label?, val constIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visit(transformer: InstTransformer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class InvokeInst(override val opcodes: Int, override var label: Label?, val invokeType: InvokeType, val mthIndex: Int): JvmInst {
    override var lineNumber: Int? = null
    override fun visit(transformer: InstTransformer) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}