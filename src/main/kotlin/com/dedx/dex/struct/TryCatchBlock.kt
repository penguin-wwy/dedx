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
