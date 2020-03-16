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

class LocalVarNode private constructor(val regNum: Int, val name: String?, val type: TypeBox?, val sign: String?) {
    var isEnd: Boolean = false
        private set
    var startAddr: Int = 0
        private set
    var endAddr: Int = 0
        private set
    companion object {
        fun create(dex: DexNode, rn: Int, nameId: Int, typeId: Int, signId: Int): LocalVarNode {
            val name = when (nameId) {
                DexNode.NO_INDEX -> null
                else -> dex.getString(nameId)
            }
            val type = when (typeId) {
                DexNode.NO_INDEX -> null
                else -> dex.getType(typeId)
            }
            val sign = when (signId) {
                DexNode.NO_INDEX -> null
                else -> dex.getString(signId)
            }
            return LocalVarNode(rn, name, type, sign)
        }

        fun create(arg: InstArgNode) = LocalVarNode(arg.regNum, arg.getName(), arg.type, null)

        fun create(rn: Int, name: String?, type: TypeBox?) = LocalVarNode(rn, name, type, null)
    }

    fun start(addr: Int) {
        isEnd = false
        startAddr = addr
    }

    fun end(addr: Int): Boolean {
        if (isEnd) {
            return false
        }
        isEnd = true
        endAddr = addr
        return true
    }
}
