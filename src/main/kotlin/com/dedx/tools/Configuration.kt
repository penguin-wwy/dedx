package com.dedx.tools

object Configuration {
    const val NormalFast = 0
    const val NormalOpt = 1
    const val Optimized = 2
    var optLevel = 1
    lateinit var outDir: String
    lateinit var dexFiles: MutableList<String>
    lateinit var classesList: MutableList<String>

    var logFile: String? = null
    var debug: Boolean = false
}