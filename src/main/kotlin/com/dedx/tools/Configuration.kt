package com.dedx.tools

open class Configuration {
    var optLevel = NormalOpt
    val classesList: MutableList<String> = ArrayList()
    val blackClasses: MutableList<String> = ArrayList()

    lateinit var outDir: String
    lateinit var dexFiles: MutableList<String>

    var logFile: String? = null
    var debug: Boolean = false

    var successNum = 0
    var failedNum = 0

    companion object {
        const val NormalFast = 0
        const val NormalOpt = 1
        const val Optimized = 2
    }

    @Synchronized
    fun addSuccess(num: Int = 1) = also {
        successNum += num
    }

    @Synchronized
    fun addFailed(num: Int = 1) = also {
        failedNum += num
    }
}

val CmdConfiguration = Configuration()
val EmptyConfiguration = Configuration()