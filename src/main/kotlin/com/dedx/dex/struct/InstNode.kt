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

package com.dedx.dex.struct

import com.android.dx.io.instructions.DecodedInstruction
import org.objectweb.asm.Label

class InstNode(val cursor: Int, val instruction: DecodedInstruction) : AttrNode {
    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()

    fun opcode() = instruction.opcode

    fun target() = instruction.target

    fun setLineNumber(line: Int) {
        attributes[AttrKey.LINENUMBER] = AttrValue(Enc.ENC_INT, line)
    }

    fun getLineNumber() = attributes[AttrKey.LINENUMBER]?.getAsInt()

    fun setLable(label: Label) {
        attributes[AttrKey.LABEL] = AttrValueLabel(label)
    }

    fun getLabel() = when (attributes.containsKey(AttrKey.LABEL)) {
        true -> attributes[AttrKey.LABEL] as AttrValueLabel
        false -> null
    }

    fun getLabelOrPut() = attributes.getOrPut(AttrKey.LABEL) {
        return@getOrPut AttrValueLabel(Label())
    } as AttrValueLabel

    fun setTryEntry(block: TryCatchBlock) {
        attributes[AttrKey.TRY_ENTRY] = AttrValue(Enc.ENC_TRY_ENTRY, block)
    }

    fun getTryEntry() = attributes[AttrKey.TRY_ENTRY]?.getAsTryEntry()

    override fun toString(): String {
        return instruction.toString()
    }
}
