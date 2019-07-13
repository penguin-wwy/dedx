package com.dedx.struct

interface DexNodeFactory<T> {
    fun create(filePath: String): T?
}