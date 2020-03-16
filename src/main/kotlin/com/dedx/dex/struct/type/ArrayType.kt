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

package com.dedx.dex.struct.type

class ArrayType(val subType: TypeBox) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ArrayType) {
            return false
        }
        return subType == other.subType
    }

    fun descriptor(): String {
        var desc = StringBuilder("[")
        var subType = this.subType
        loop@ while (true) {
            when (subType.type::class) {
                ArrayType::class -> {
                    desc.append("[")
                    subType = (subType.type as ArrayType).subType
                }
                else -> {
                    desc.append(subType.descriptor())
                    break@loop
                }
            }
        }
        return desc.toString()
    }

    fun nameWithSlash(): String {
        var name = StringBuilder("[")
        var subType = this.subType
        loop@ while (true) {
            when (subType.type::class) {
                ArrayType::class -> {
                    name.append("[")
                    subType = (subType.type as ArrayType).subType
                }
                ObjectType::class -> {
                    name.append((subType.type as ObjectType).nameWithSlash())
                    break@loop
                }
                else -> {
                    name.append(subType.descriptor())
                    break@loop
                }
            }
        }
        return name.toString()
    }

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return "$subType[]"
    }
}
