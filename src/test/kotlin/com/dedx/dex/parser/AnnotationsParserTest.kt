package com.dedx.dex.parser

import com.android.dex.Dex
import com.dedx.dex.struct.DexNode
import org.junit.Assert.*
import org.junit.Test

class AnnotationsParserTest {
    @Test
    fun testAnnotationsParser() {
        val bytes = AnnotationsParser::class.java.getResource("/AnnotationTest.dex").openStream().readBytes()
        val dexNode = DexNode.create(bytes)
        dexNode.loadClass()
    }
}