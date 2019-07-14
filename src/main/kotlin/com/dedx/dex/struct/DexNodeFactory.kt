package com.dedx.dex.struct

interface DexNodeFactory<T> {
    fun create(filePath: String): T?
}