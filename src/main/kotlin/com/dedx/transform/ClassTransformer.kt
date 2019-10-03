package com.dedx.transform

import com.android.dx.rop.code.AccessFlags
import com.dedx.dex.struct.ClassInfo
import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.MethodNode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes.V1_8
import java.io.File
import java.io.FileOutputStream

class ClassTransformer(val clsNode: ClassNode, val filePath: String = "") {
    val classWriter = ClassWriter(0)
    lateinit var fieldVisitor: FieldVisitor

    fun visitClass(): ClassTransformer {
        classWriter.visit(V1_8,
                if (!clsNode.isInterface()) clsNode.accFlags or AccessFlags.ACC_SUPER else clsNode.accFlags,
                clsNode.clsInfo.fullName.replace('.', '/'),
                null,
                if (clsNode.hasSuperClass()) clsNode.superClassNameWithSlash() else ClassInfo.ROOT_CLASS_NAME,
                if (clsNode.interfaces.isNotEmpty()) Array(clsNode.interfaces.size) {
                    i -> clsNode.interfaces[i].nameWithSlash()
                } else null)

        // TODO: set source file need debug info
//        classWriter.visitSource()

        clsNode.fields.forEach { field -> run {
            fieldVisitor = classWriter.visitField(field.accFlags, field.fieldInfo.name, field.fieldInfo.type.descriptor(), null, null)
            // TODO: fieldVisitor.visitAnnotation()
            fieldVisitor.visitEnd()
        } }

        var main: MethodNode? = null
        for (mthNode in clsNode.methods) {
            if (mthNode.isMain()) {
                main = mthNode
                continue
            }
            MethodTransformer(mthNode, this).visitMethod()
        }
        if (main != null) MethodTransformer(main, this).visitMethod()
        classWriter.visitEnd()
        return this
    }

    fun dump(): String {
            FileOutputStream(File(filePath)).use {
                w -> w.write(classWriter.toByteArray())
            }
            return filePath
    }

    fun toFile(path: String) = FileOutputStream(File(path)).write(classWriter.toByteArray())
}