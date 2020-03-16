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

class FieldInfo(val declClass: ClassInfo, val name: String, val type: TypeBox) {

    companion object {
        fun from(dex: DexNode, declClass: ClassInfo, name: String, type: TypeBox): FieldInfo {
            return FieldInfo(declClass, name, type)
        }

        fun fromDex(dex: DexNode, index: Int): FieldInfo {
            val field = dex.getFieldId(index)
            return from(dex,
                    ClassInfo.fromDex(dex, field.declaringClassIndex),
                    dex.getString(field.nameIndex),
                    dex.getType(field.typeIndex))
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is FieldInfo) {
            return false
        }
        return declClass == other.declClass && name == other.name && type == other.type
    }

    override fun toString(): String {
        return "$declClass $name"
    }
}
