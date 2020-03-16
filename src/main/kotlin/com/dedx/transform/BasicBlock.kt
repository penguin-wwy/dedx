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

import com.dedx.dex.struct.InstNode
import org.objectweb.asm.Label

class BasicBlock constructor(
    val startLable: Label,
    val predecessor: ArrayList<BasicBlock>,
    val successor: ArrayList<BasicBlock>
) {

    constructor(startLable: Label, predecessor: ArrayList<BasicBlock>, successor: ArrayList<BasicBlock>, cursor: Int) :
            this(startLable, predecessor, successor) {
        firstCursor = cursor
    }

    var terminal: InstNode? = null
    val instList = ArrayList<InstNode>()
    private var firstCursor: Int? = null

    companion object {
        fun create(startLable: Label, predecessor: BasicBlock?): BasicBlock {
            val preList = ArrayList<BasicBlock>()
            val succList = ArrayList<BasicBlock>()
            if (predecessor != null) {
                preList.add(predecessor!!)
            }
            return BasicBlock(startLable, preList, succList)
        }
    }

    fun firstCursor(): Int? {
        if (firstCursor != null) {
            return firstCursor
        }
        if (instList.isNotEmpty()) {
            firstCursor = instList[0].cursor
            return firstCursor
        }
        return null
    }
}
