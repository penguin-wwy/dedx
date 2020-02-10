package com.dedx.dex.struct.type

import org.junit.Assert.assertTrue
import org.junit.Test

class TypeBoxTest {

    @Test
    fun testCreateBox() {
        val intBox = TypeBox.create("I")
        assertTrue(intBox.getAsBasicType()?.mark == BasicType.INT.mark)

        val objectBox = TypeBox.create("Ljava/lang/String;")
        assertTrue(objectBox.getAsObjectType()?.typeName.equals("java.lang.String"))

        val arrayBox = TypeBox.create("[B")
        assertTrue(arrayBox.getAsArrayType()?.subType?.getAsBasicType()?.mark == BasicType.BYTE.mark)
    }
}
