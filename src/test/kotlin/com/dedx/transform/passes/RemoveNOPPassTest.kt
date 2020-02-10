package com.dedx.transform.passes

import com.dedx.EmptyResource
import com.dedx.transform.*
import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.Opcodes

class RemoveNOPPassTest {

    private fun createNOPInstTrans(): InstTransformer {
        val instTransformer = InstTransformer(MethodTransformer(EmptyResource.emptyMethodNode, ClassTransformer(EmptyResource.classNode)))
        instTransformer.pushJvmInst(JvmInst.CreateSlotInst(Opcodes.ISTORE, 0))
        instTransformer.pushJvmInst(JvmInst.CreateSingleInst(Opcodes.NOP))
        instTransformer.pushJvmInst(JvmInst.CreateSlotInst(Opcodes.ILOAD, 0))
        instTransformer.pushJvmInst(JvmInst.CreateLiteralInst(Opcodes.LDC, 100, SlotType.INT))
        instTransformer.pushJvmInst(JvmInst.CreateSingleInst(Opcodes.IADD))
        instTransformer.pushJvmInst(JvmInst.CreateSlotInst(Opcodes.ISTORE, 0))
        return instTransformer
    }

    @Test
    fun testRemoveNOP() {
        val instTransformer = createNOPInstTrans()
        RemoveNOPPass.runOnFunction(instTransformer)
        assertEquals(5, instTransformer.instListSize())
    }
}
