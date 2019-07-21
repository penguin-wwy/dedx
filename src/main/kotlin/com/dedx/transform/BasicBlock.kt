package com.dedx.transform

import org.objectweb.asm.Label

class BasicBlock private constructor(val startLable: Label, val predecessor: ArrayList<BasicBlock>, val successor: ArrayList<BasicBlock>) {

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
}