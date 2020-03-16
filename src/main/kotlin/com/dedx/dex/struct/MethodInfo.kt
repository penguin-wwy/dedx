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

class MethodInfo private constructor(
    val name: String,
    val retType: TypeBox,
    val args: List<TypeBox>,
    val declClass: ClassInfo
) {

    var descriptor: String? = null
    companion object {
        fun create(dex: DexNode, mthId: Int): MethodInfo {
            val mthId = dex.getMethodId(mthId)
            val name = dex.getString(mthId.nameIndex)
            val declClass = ClassInfo.fromDex(dex, mthId.declaringClassIndex)

            val proto = dex.getProtoId(mthId.protoIndex)
            val retType = dex.getType(proto.returnTypeIndex)
            val args = dex.getTypeList(proto.parametersOffset)
            return MethodInfo(name, retType, args, declClass)
        }

        fun fromDex(dex: DexNode, mthId: Int): MethodInfo {
            var mth = InfoStorage.getMethod(dex, mthId)
            if (mth != null) {
                return mth
            }
            mth = create(dex, mthId)
            return InfoStorage.putMethod(dex, mthId, mth)
        }
    }

    fun parseSignature(): String {
        if (this.descriptor == null) {
            val descriptor = StringBuilder("(")
            for (arg in args) {
                descriptor.append(arg.descriptor())
            }
            descriptor.append(")${retType.descriptor()}")
            this.descriptor = descriptor.toString()
        }
        return this.descriptor!!
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MethodInfo) {
            return false
        }
        return declClass == other.declClass && name == other.name && retType == other.retType && args == other.args
    }

    override fun toString(): String {
        return "$declClass $name ${parseSignature()}"
    }
}
