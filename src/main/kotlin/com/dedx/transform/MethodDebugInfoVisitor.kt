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

import com.dedx.dex.parser.DebugInfoParser
import com.dedx.dex.struct.InstNode
import com.dedx.dex.struct.LocalVarNode
import com.dedx.dex.struct.MethodNode

object MethodDebugInfoVisitor {

    fun visitMethod(mthNode: MethodNode) {
        if (mthNode.debugInfoOffset == 0) {
            return
        }
        val insnArr = mthNode.codeList
        val parser = DebugInfoParser(mthNode, insnArr, mthNode.debugInfoOffset)
        val locals = parser.process()
        attachDebugInfo(mthNode, locals, insnArr)
        attachSourceInfo(mthNode, insnArr)
    }

    fun attachDebugInfo(mthNode: MethodNode, locals: List<LocalVarNode>, insts: Array<InstNode?>) {
        // TODO
    }

    fun attachSourceInfo(mthNode: MethodNode, insts: Array<InstNode?>) {
        for (inst in insts) {
            if (inst != null) {
                val line = inst.getLineNumber()
                if (line != null) {
                    mthNode.setLineNumber(line - 1)
                }
                return
            }
        }
    }
}
