package com.dedx.dex.struct

import com.android.dx.rop.code.AccessFlags

interface AccessInfo {
    val accFlags: Int
    fun isPublic() = (accFlags and AccessFlags.ACC_PUBLIC) != 0
    fun isProtected() = (accFlags and AccessFlags.ACC_PROTECTED) != 0
    fun isPrivate() = (accFlags and AccessFlags.ACC_PRIVATE) != 0
    fun isFinal() = (accFlags and AccessFlags.ACC_FINAL) != 0
    fun isStatic() = (accFlags and AccessFlags.ACC_STATIC) != 0
    fun isAbstract() = (accFlags and AccessFlags.ACC_ABSTRACT) != 0
    fun isInterface() =  (accFlags and AccessFlags.ACC_INTERFACE) != 0
}