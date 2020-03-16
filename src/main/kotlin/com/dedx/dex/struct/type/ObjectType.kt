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

open class ObjectType(val typeName: String) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is ObjectType) {
            return false
        }
        return typeName == other.typeName
    }

    fun descriptor() = "L${nameWithSlash()};"

    fun nameWithDot() = typeName

    fun nameWithSlash() = typeName.replace('.', '/')

    override fun hashCode(): Int {
        return toString().hashCode()
    }

    override fun toString(): String {
        return typeName
    }
}
