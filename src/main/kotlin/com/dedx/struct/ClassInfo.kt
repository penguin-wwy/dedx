package com.dedx.struct

import com.android.dex.Dex

class ClassInfo {

    companion object {
        fun fromDex(dex: Dex, index: Int): ClassInfo {
            return ClassInfo()
        }
    }
}