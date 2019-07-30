package com.dedx.transform

class StackFrame {
    val reg2slot = HashMap<Int, Int?>()
    val slot2reg = HashMap<Int, Int?>()
    val preFrames = ArrayList<StackFrame>()
    val sucFrames = ArrayList<StackFrame>()

    fun init(): StackFrame {
        reg2slot.clear()
        slot2reg.clear()
        return this
    }
}