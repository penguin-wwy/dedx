package com.dedx.struct

interface Factory<T> {
    fun create(): T
}