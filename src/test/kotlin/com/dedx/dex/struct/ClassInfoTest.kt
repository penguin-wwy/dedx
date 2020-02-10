package com.dedx.dex.struct

import com.dedx.dex.struct.type.ObjectType
import com.dedx.dex.struct.type.TypeBox
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassInfoTest {
    @Test
    fun testClassInfo() {
        val info = ClassInfo.fromType(TypeBox.create(ObjectType("java.lang.String")))
        assertTrue(info.fullName == "java.lang.String")
        assertTrue(info.pkg == "java.lang")
        assertTrue(info.name == "String")
        assertFalse(info.isInner)
    }
}
