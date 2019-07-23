package com.dedx.dex.struct

import com.dedx.dex.struct.type.TypeBox

class LocalVarNode private constructor(val regNum: Int, val name: String?, val type: TypeBox?, val sign: String?) {

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
    }
}