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

import com.android.dex.ClassData

interface FieldFactory {
    fun create(parent: ClassNode, field: ClassData.Field): FieldNode
}

class FieldNode private constructor(
    val parent: ClassNode,
    val fieldInfo: FieldInfo,
    access: Int
) : AccessInfo, AttrNode {
    override val accFlags: Int = access
    override var attributes: MutableMap<AttrKey, AttrValue> = HashMap()
    companion object : FieldFactory {
        override fun create(parent: ClassNode, field: ClassData.Field): FieldNode {
            val accessFlag = field.accessFlags
            return FieldNode(parent, FieldInfo.fromDex(parent.parent, field.fieldIndex), accessFlag)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FieldNode) {
            return false
        }
        return fieldInfo == other.fieldInfo
    }
}
