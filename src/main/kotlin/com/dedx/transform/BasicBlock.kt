package com.dedx.transform

import com.dedx.dex.struct.InstNode
import org.objectweb.asm.Label

class BasicBlock constructor(
    val startLable: Label,
    val predecessor: ArrayList<BasicBlock>,
    val successor: ArrayList<BasicBlock>
) {

    constructor(startLable: Label, predecessor: ArrayList<BasicBlock>, successor: ArrayList<BasicBlock>, cursor: Int) :
            this(startLable, predecessor, successor) {
        firstCursor = cursor
    }

    var terminal: InstNode? = null
    val instList = ArrayList<InstNode>()
    private var firstCursor: Int? = null

    companion object {
        fun create(startLable: Label, predecessor: BasicBlock?): BasicBlock {
            val preList = ArrayList<BasicBlock>()
            val succList = ArrayList<BasicBlock>()
            if (predecessor != null) {
                preList.add(predecessor!!)
            }
            return BasicBlock(startLable, preList, succList)
        }
    }

    fun firstCursor(): Int? {
        if (firstCursor != null) {
            return firstCursor
        }
        if (instList.isNotEmpty()) {
            firstCursor = instList[0].cursor
            return firstCursor
        }
        return null
    }
}
