package com.dedx.dex.parser

import com.android.dex.Dex
import com.dedx.dex.struct.*
import com.dedx.dex.struct.Annotation
import com.dedx.utils.DecodeException

class AnnotationsParser(val dex: DexNode, val cls: ClassNode) {

    companion object {
        private val VISIBILITIES = enumValues<Visibility>()

        fun readAnnotation(dex: DexNode, section: Dex.Section, readVisibility: Boolean): Annotation {
            val parser = EncValueParser(dex, section)
            var visibility: Visibility? = null
            if (readVisibility) {
                val v = section.readByte()
                visibility = VISIBILITIES[v.toInt()]
            }
            val typeIndex = section.readUleb128()
            val size = section.readUleb128()
            val values = LinkedHashMap<String, AttrValue>(size)
            for (i in 0 until size) {
                val name = dex.getString(section.readUleb128())
                values[name] = parser.parseValue()
            }
            val type = dex.getType(typeIndex)
            val annotation = Annotation(visibility, type, values)
            if (type.getAsObjectType() == null) {
                throw DecodeException("Incorrect type for annotation: $annotation")
            }
            return annotation
        }
    }

    fun parse(offset: Int) {
        val section = dex.dex.open(offset)

        val classAnnotationOffset = section.readInt()
        val fieldsCount = section.readInt()
        val annotatedMethodCount = section.readInt()
        val annotationParametersCount = section.readInt()

        if (classAnnotationOffset != 0) {
            cls.setValue(AttrKey.ANNOTATION, readAnnotationSet(classAnnotationOffset))
        }

        for (i in 0 until fieldsCount) {
            val fieldNode = cls.searchFieldById(section.readInt())
            if (fieldNode?.setValue(AttrKey.ANNOTATION, readAnnotationSet(section.readInt())) == null) {
                // out log
            }
        }

        for (i in 0 until annotatedMethodCount) {
            val methodNode = cls.searchMethodById(section.readInt())
            if (methodNode?.setValue(AttrKey.ANNOTATION, readAnnotationSet(section.readInt())) == null) {
                // out log
            }
        }

        for (i in 0 until annotationParametersCount) {
            // TODO
        }
    }

    private fun readAnnotationSet(offset: Int): AttrValueList {
        if (offset == 0) {
            return AttrValueList.EMPTY
        }
        val section = dex.dex.open(offset)
        val size = section.readInt()
        if (size == 0) {
            return AttrValueList.EMPTY
        }
        val list = ArrayList<AttrValue>(size)
        for (i in 0 until size) {
            val anSection = dex.dex.open(section.readInt())
            val annotation = readAnnotation(dex, anSection, true)
            list.add(AttrValue(EncValueParser.ENC_ANNOTATION, annotation))
        }
        return AttrValueList(list)
    }
}