package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class LocalVarNode private constructor(val regNum: Int, val name: String?, val type: TypeBox?, val sign: String?) {
    var isEnd: Boolean = false
        private set
    var startAddr: Int = 0
        private set
    var endAddr: Int = 0
        private set
    companion object {
        fun create(dex: DexNode, rn: Int, nameId: Int, typeId: Int, signId: Int): LocalVarNode {
            val name = when (nameId) {
                DexNode.NO_INDEX -> null
                else -> dex.getString(nameId)
            }
            val type = when (typeId) {
                DexNode.NO_INDEX -> null
                else -> dex.getType(typeId)
            }
            val sign = when (signId) {
                DexNode.NO_INDEX -> null
                else -> dex.getString(signId)
            }
            return LocalVarNode(rn, name, type, sign)
        }

        fun create(arg: InstArgNode) = LocalVarNode(arg.regNum, arg.getName(), arg.type, null)

        fun create(rn: Int, name: String?, type: TypeBox?) = LocalVarNode(rn, name, type, null)
    }

    fun start(addr: Int) {
        isEnd = false
        startAddr = addr
    }

    fun end(addr: Int): Boolean {
        if (isEnd) {
            return false
        }
        isEnd = true
        endAddr = addr
        return true
    }
}
