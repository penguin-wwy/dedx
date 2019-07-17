package com.dedx.transform

import com.dedx.dex.struct.ClassNode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.V1_8
import java.io.File
import java.io.FileOutputStream

class ClassTransformer(val clsNode: ClassNode, val filePath: String): Opcodes {
    val classWriter = ClassWriter(0)
    var fieldVisitor: FieldVisitor? = null

    fun visitClass(): ClassTransformer {
        var superName = "java/lang/object"
        if (clsNode.clsInfo.parentClass != null) {
            superName = clsNode.clsInfo.parentClass.fullName.replace('.', '/')
        }
        classWriter.visit(V1_8,
                clsNode.accFlags,
                clsNode.clsInfo.fullName.replace('.', '/'),
                null,
                superName,
                null/*TODO interfaces*/)

        // TODO
//        classWriter.visitSource()
        for (mthNode in clsNode.methods) {

        }
        classWriter.visitEnd()
        return this
    }

    fun dump() {
        val outputStream = FileOutputStream(File(filePath))
        outputStream.write(classWriter.toByteArray())
    }
}