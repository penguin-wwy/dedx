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

object InfoStorage {
    val classes: MutableMap<TypeBox, ClassInfo> = HashMap()
    val methods: MutableMap<Int, MethodInfo> = HashMap()
    val fields: MutableMap<FieldInfo, FieldInfo> = HashMap()

    fun getMethod(dex: DexNode, mthId: Int): MethodInfo? {
        return methods[mthId]
    }

    fun putMethod(dex: DexNode, mthId: Int, mth: MethodInfo): MethodInfo {
        methods[mthId] = mth
        return mth
    }

    fun clear() {
        classes.clear()
        methods.clear()
        fields.clear()
    }
}
