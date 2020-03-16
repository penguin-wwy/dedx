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

package com.dedx.dex.parser

import com.android.dex.Dex
import com.dedx.dex.struct.Annotation
import com.dedx.dex.struct.AttrKey
import com.dedx.dex.struct.AttrValue
import com.dedx.dex.struct.AttrValueList
import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.DexNode
import com.dedx.dex.struct.Enc
import com.dedx.dex.struct.Visibility
import com.dedx.utils.DecodeException
import com.google.common.flogger.FluentLogger

class AnnotationsParser(val dex: DexNode, val cls: ClassNode) {

    companion object {
        private val logger = FluentLogger.forEnclosingClass()

        private val VISIBILITIES = enumValues<Visibility>()

        fun readAnnotation(dex: DexNode, section: Dex.Section, readVisibility: Boolean): Annotation {
            val parser = EncValueParser(dex, section)
            var visibility: Visibility? = null
            if (readVisibility) {
                val v = section.readByte().toUByte()
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
                logger.atWarning().log("Not find [${cls.clsInfo.fullName}] field " +
                        "to add annotation ${readAnnotationSet(section.readInt())}")
            }
        }

        for (i in 0 until annotatedMethodCount) {
            val methodNode = cls.searchMethodById(section.readInt())
            if (methodNode?.setValue(AttrKey.ANNOTATION, readAnnotationSet(section.readInt())) == null) {
                logger.atWarning().log("Not find [${cls.clsInfo.fullName}] method " +
                        "to add annotation ${readAnnotationSet(section.readInt())}")
            }
        }

        for (i in 0 until annotationParametersCount) {
            val methodNode = cls.searchMethodById(section.readInt())
            val ss = dex.openSection(section.readInt())
            val size = ss.readInt()
            val annotationList = ArrayList<AttrValueList>()
            for (index in 0 until size) {
                annotationList.add(readAnnotationSet(ss.readInt()))
            }
            if (methodNode?.setValue(AttrKey.MTH_PARAMETERS_ANNOTATION, AttrValueList(annotationList)) == null) {
                logger.atWarning().log("Not find [${cls.clsInfo.fullName}] method " +
                        "to add parameters annotation ${AttrValueList(annotationList)}")
            }
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
            list.add(AttrValue(Enc.ENC_ANNOTATION, annotation))
        }
        return AttrValueList(list)
    }
}
