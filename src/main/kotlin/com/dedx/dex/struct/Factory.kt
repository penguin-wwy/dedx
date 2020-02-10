package com.dedx.dex.struct

interface Factory<T> {
    fun create(): T
}
