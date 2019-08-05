package com.dedx.utils

class BlockEmptyException: Exception {

    constructor(message: String): super(message)

    constructor(message: String, cause: Throwable): super(message, cause)

}