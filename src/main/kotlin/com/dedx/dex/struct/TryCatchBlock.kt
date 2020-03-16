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

import java.util.TreeSet
import kotlin.collections.ArrayList

class ExceptionHandler(var handlerBlock: TryCatchBlock, val addr: Int, type: ClassInfo?) {
    var catchAny = false
    val catchTypes = TreeSet<ClassInfo>()
    init {
        if (type == null) {
            catchAny = true
        } else {
            catchTypes.add(type)
        }
    }

    fun addType(type: ClassInfo?) {
        if (type == null) {
            catchAny = true
        } else {
            catchTypes.add(type)
        }
    }

    fun addException(other: ExceptionHandler) {
        for (type in other.catchTypes) {
            catchTypes.add(type)
        }
        if (other.catchAny) {
            catchAny = true
        }
    }

    fun typeList(): List<String?> {
        val result = ArrayList<String?>()
        for (type in catchTypes) {
            result.add(type.className())
        }
        if (catchAny) result.add(null)
        return result
    }
}

class TryCatchBlock {

    val execHandlers = ArrayList<ExceptionHandler>()
    val instList = ArrayList<InstNode>()

    fun addHandler(mthNode: MethodNode, addr: Int, type: ClassInfo?) {
        val exceHandler = ExceptionHandler(this, addr, type)
        val addHandler = mthNode.addExceptionHandler(exceHandler)
        if ((addHandler == exceHandler) || (addHandler.handlerBlock != this)) {
            execHandlers.add(addHandler)
        }
    }

    fun containsAllHandlers(other: TryCatchBlock) = execHandlers.containsAll(other.execHandlers)

    fun removeSameHandlers(other: TryCatchBlock) {
        for (handler in other.execHandlers) {
            if (execHandlers.remove(handler)) {
                handler.handlerBlock = other
            }
        }
    }
}
