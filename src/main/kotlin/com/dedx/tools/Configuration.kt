/*
* Copyright 2019 penguin_wwy<940375606@qq.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

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
