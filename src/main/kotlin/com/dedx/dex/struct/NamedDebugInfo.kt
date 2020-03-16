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

import com.dedx.dex.struct.type.TypeBox

class NamedDebugInfo(override val value: MutableList<AttrValue> = MutableList(5) { i -> AttrValue.Empty }) :
    AttrValueList(value) {
    companion object {
        val START = 0
        val END = 1
        val REG = 2
        val NAME = 3
        val TYPE = 4
    }

    fun getStart() = value[START].getAsInt()

    fun setStart(num: Int) {
        value[START] = AttrValue(Enc.ENC_INT, num)
    }

    fun getEnd() = value[END].getAsInt()

    fun setEnd(num: Int) {
        value[END] = AttrValue(Enc.ENC_INT, num)
    }

    fun getREG() = value[REG].getAsInt()

    fun setREG(num: Int) {
        value[REG] = AttrValue(Enc.ENC_INT, num)
    }

    fun getName() = value[NAME].getAsString()

    fun setName(name: String) {
        value[NAME] = AttrValue(Enc.ENC_STRING, name)
    }

    fun getType() = value[TYPE].getAsType()

    fun setType(type: TypeBox) {
        value[TYPE] = AttrValue(Enc.ENC_TYPE, type)
    }
}
