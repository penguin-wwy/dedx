package com.dedx.utils

import com.dedx.transform.StackFrame

class DecodeException: Exception {

    constructor(message: String): super(message)

    constructor(message: String, cause: Throwable): super(message, cause)

    constructor(message: String, offset: Int): this("$message [offset: $offset]\n${StackFrame.getFrameOrPut(offset)}")

    constructor(message: String, offset: Int, cause: Throwable): this("$message [offset: $offset]\n" +
            "${StackFrame.getFrameOrPut(offset)}", cause)
}