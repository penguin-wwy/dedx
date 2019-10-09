package com.dedx.transform.passes

import com.dedx.EmptyResource
import com.dedx.transform.*
import org.junit.Assert.*
import org.junit.Test
import org.objectweb.asm.Opcodes

class EliminateCodePassTest {

    fun createLoadAndStoreInstTrans(): InstTransformer {
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
    fun testEliminateLoadAndStore() {
        val instTransformer = createLoadAndStoreInstTrans()
        EliminateCodePass.runOnFunction(instTransformer)
        assertEquals(4, instTransformer.instListSize())
    }
}