package com.dedx.utils

class DataFlowAnalyzeException : Exception {

    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
