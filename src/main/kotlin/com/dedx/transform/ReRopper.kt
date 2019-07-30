package com.dedx.transform

import com.dedx.dex.struct.InstNode

class ReRopper(size: Int) {
    val frames = arrayOfNulls<StackFrame>(size)

    fun getOrCreateFrame(index: Int): StackFrame {
        if (frames[index] == null) {
            frames[index] = StackFrame().init()
        }
        return frames[index]!!
    }
}