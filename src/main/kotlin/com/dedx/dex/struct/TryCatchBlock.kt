package com.dedx.dex.struct

import java.util.*

class ExceptionHandler(var handlerBlock: TryCatchBlock, val addr: Int, type: ClassInfo?) {
    val catchTypes = TreeSet<ClassInfo?>()
    init {
        catchTypes.add(type)
    }
}

class TryCatchBlock {

    val execHandlers = ArrayList<ExceptionHandler>()
    val instList = ArrayList<InstNode>()

    fun addHandler(mthNode: MethodNode, addr: Int, type: ClassInfo?) {
        val exceHandler = ExceptionHandler(this, addr, type)
        val addHandler = mthNode.addExceptionHandler(exceHandler)
        if ((addHandler == exceHandler) or (addHandler.handlerBlock != this)) {
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