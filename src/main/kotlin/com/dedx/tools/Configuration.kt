package com.dedx.tools

open class Configuration {
    var optLevel = NormalOpt
    val classesList: MutableList<String> = ArrayList()
    val blackClasses: MutableList<String> = ArrayList()

    lateinit var outDir: String
    lateinit var dexFiles: MutableList<String>

    var logFile: String? = null
    var debug: Boolean = false

    companion object {
        const val NormalFast = 0
        const val NormalOpt = 1
        const val Optimized = 2
    }
}

val CmdConfiguration = Configuration()
val EmptyConfiguration = Configuration()