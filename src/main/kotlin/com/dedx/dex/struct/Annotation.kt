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

enum class Visibility {
    BUILD,
    RUNTIME,
    SYSTEM
}

class Annotation(val visibility: Visibility?, val type: TypeBox, val values: Map<String, AttrValue>) {
    fun getDefaultValue(): Any? {
        return values["value"]
    }

    fun hasVisibility() = visibility != null

    override fun toString(): String {
        val strBuilder = StringBuilder("{")
        for (entry in values) {
            strBuilder.append("${entry.key}:${entry.value},")
        }
        strBuilder.deleteCharAt(strBuilder.length - 1)
        return strBuilder.toString()
    }
}
