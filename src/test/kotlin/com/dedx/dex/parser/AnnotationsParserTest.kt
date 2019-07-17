package com.dedx.dex.parser

import com.dedx.dex.struct.AttrKey
import com.dedx.dex.struct.DexNode
import org.junit.Assert.*
import org.junit.Test

class AnnotationsParserTest {
    @Test
    fun testAnnotationsParser() {
        val bytes = AnnotationsParser::class.java.getResource("/AnnotationTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
        for (clsNode in dexNode.classes) {
            if (clsNode.clsInfo.name == "AnnotationTest") {
                assertEquals(clsNode.attributes[AttrKey.ANNOTATION].toString(), "[{value:AnnotationTest]")
            }
        }
    }
}