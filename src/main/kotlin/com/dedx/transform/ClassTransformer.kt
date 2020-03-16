/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.dedx.transform

import com.android.dx.rop.code.AccessFlags
import com.dedx.dex.struct.AttrKey
import com.dedx.dex.struct.ClassInfo
import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.FieldNode
import com.dedx.tools.Configuration
import com.dedx.tools.EmptyConfiguration
import com.google.common.flogger.FluentLogger
import java.io.File
import java.io.FileOutputStream
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes.V1_8

class ClassTransformer(
    private val clsNode: ClassNode,
    val config: Configuration = EmptyConfiguration,
    private val filePath: String = ""
) {
    val classWriter = ClassWriter(1)
    lateinit var fieldVisitor: FieldVisitor
    lateinit var annotationVisitor: AnnotationVisitor
    private val sourceFile = clsNode.getSourceFile()

    private var success = 0
    private var failed = 0

    companion object {
        private val logger = FluentLogger.forEnclosingClass()
    }

    fun visitClass() = apply {
        classWriter.visit(V1_8,
                if (!clsNode.isInterface()) clsNode.accFlags or AccessFlags.ACC_SUPER else clsNode.accFlags,
                clsNode.clsInfo.fullName.replace('.', '/'),
                null,
                if (clsNode.hasSuperClass()) clsNode.superClassNameWithSlash() else ClassInfo.ROOT_CLASS_NAME,
                if (clsNode.interfaces.isNotEmpty()) Array(clsNode.interfaces.size) { i ->
                    clsNode.interfaces[i].nameWithSlash()
                } else null)

        visitClassAnnotation()
        visitField()
        visitMethod()
        classWriter.visitSource(sourceFile, null)
        classWriter.visitEnd()
        config.addSuccess(success).addFailed(failed)
        logger.atInfo().log("[${clsNode.clsInfo} end] Success/Failed $success/$failed")
    }

    private fun visitMethod() {
        clsNode.methods.forEach {
            if (MethodTransformer(it, this).visitMethodAnnotation().visitMethodBody()) {
                success++
            } else {
                failed++
            }
        }
    }

    private fun visitField() {
        val annotationVisit = fun (fn: FieldNode, fv: FieldVisitor) {
            val annoList = fn.attributes[AttrKey.ANNOTATION]?.getAsAttrValueList() ?: return
            for (value in annoList) {
                val annoClazz = value.getAsAnnotation() ?: continue
                annotationVisitor = fv.visitAnnotation(annoClazz.type.descriptor(), annoClazz.hasVisibility())
                for (annotationValue in annoClazz.values) {
                    annotationVisitor.visit(annotationValue.key, annotationValue.value.value)
                }
                annotationVisitor.visitEnd()
            }
        }
        clsNode.fields.forEach { field ->
            run {
                fieldVisitor = classWriter.visitField(field.accFlags, field.fieldInfo.name, field.fieldInfo.type.descriptor(), null, null)
                annotationVisit(field, fieldVisitor)
                fieldVisitor.visitEnd()
            }
        }
    }

    private fun visitClassAnnotation() {
        val annoList = clsNode.attributes[AttrKey.ANNOTATION]?.getAsAttrValueList() ?: return
        for (value in annoList) {
            val annoClazz = value.getAsAnnotation() ?: continue
            annotationVisitor = classWriter.visitAnnotation(annoClazz.type.descriptor(), annoClazz.hasVisibility())
            for (annotationValue in annoClazz.values) {
                try {
                    annotationVisitor.visit(annotationValue.key, annotationValue.value.value)
                } catch (e: IllegalArgumentException) {
                    logger.atWarning().withCause(e).log(logInfo())
                }
            }
            annotationVisitor.visitEnd()
        }
    }

    private fun logInfo() = "${clsNode.clsInfo}"

    fun dump(): String {
        FileOutputStream(File(filePath)).use { w ->
            w.write(classWriter.toByteArray())
        }
        logger.atInfo().log("Dump ${clsNode.clsInfo.fullName} to $filePath")
        return filePath
    }

    fun toFile(path: String) = FileOutputStream(File(path)).write(classWriter.toByteArray())
}
