package com.dedx.transform.passes

import com.dedx.transform.InstTransformer

interface Pass {
    fun initializaPass()

    fun runOnFunction(instTrans: InstTransformer)
}
