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

class InstArgNode(val regNum: Int, val type: TypeBox, inst: InstNode? = null) : AttrNode {

    override val attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    val user = ArrayList<InstNode>()
    // method argument have no assign site
    val site = inst

    fun isArgument() = site == null

    fun setName(name: String) {
        attributes[AttrKey.NAME] = AttrValue(Enc.ENC_STRING, name)
    }

    fun getName() = attributes[AttrKey.NAME]?.getAsString()
}
