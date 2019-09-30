package com.dedx.transform.passes

import com.dedx.transform.InstTransformer

interface Pass {
    fun runOnFunction(instTrans: InstTransformer)
}