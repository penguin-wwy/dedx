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

import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer
import com.dedx.utils.DecodeException
import com.google.common.flogger.FluentLogger
import java.io.File
import java.io.FileReader
import java.util.logging.ConsoleHandler
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

fun configLog() {
    val root = Logger.getLogger("")
    root.level = if (CmdConfiguration.debug) Level.FINEST else Level.INFO
    root.handlers.forEach { root.removeHandler(it) }
    root.addHandler(CmdConfiguration.logFile?.let {
        FileHandler(it)
    } ?: ConsoleHandler())
}

fun createCmdTable(): Options {
    val help = Option.builder("h")
            .longOpt("help").desc("Print help message").build()
    val version = Option.builder("v")
            .longOpt("version").desc("Print version").build()
    val output = Option.builder("o")
            .longOpt("output")
            .hasArg().argName("dirname").desc("Specify output dirname").build()
    val optLevel = Option.builder()
            .longOpt("opt").hasArg().argName("[fast|normal]") // |optimize
            .desc("Specify optimization level").build()
    val logFile = Option.builder().longOpt("log").desc("Specify log file").hasArg().build()
    val debug = Option.builder("g").longOpt("debug").desc("Print debug info").build()
    val classes = Option.builder().longOpt("classes")
            .hasArg().argName("[class_name | @file]")
            .desc("Specify classes which to load (default all)").build()
    val blackList = Option.builder().longOpt("black-classes")
            .hasArg().argName("[class_name | @file]")
            .desc("Specify classes which not to load (default none)").build()

    val optTable = Options()
    optTable.addOption(help)
    optTable.addOption(version)
    optTable.addOption(output)
    optTable.addOption(optLevel)
    optTable.addOption(logFile)
    optTable.addOption(debug)
    optTable.addOption(classes)
    optTable.addOption(blackList)
    return optTable
}

fun configFromOptions(args: Array<String>, optTable: Options) {
    val parser = DefaultParser()
    try {
        val cmdTable = parser.parse(optTable, args)
        if (cmdTable.hasOption("v") || cmdTable.hasOption("version")) {
            exitProcess(0)
        }
        if (cmdTable.hasOption("h") || cmdTable.hasOption("help")) {
            HelpFormatter().printHelp("command [options] <dexfile>", optTable)
            exitProcess(0)
        }
        if (cmdTable.hasOption("o") || cmdTable.hasOption("output")) {
            CmdConfiguration.outDir = cmdTable.getOptionValue("o") ?: cmdTable.getOptionValue("output")
        }
        if (cmdTable.hasOption("opt")) {
            CmdConfiguration.optLevel = when (cmdTable.getOptionValue("opt")) {
                "fast" -> Configuration.NormalFast
                "normal" -> Configuration.NormalOpt
//                "optimize" -> Configuration.Optimized
                else -> {
                    throw RuntimeException("Invalid parameter for 'opt'")
                }
            }
        }
        if (cmdTable.hasOption("log")) {
            CmdConfiguration.logFile = cmdTable.getOptionValue("log")
        }
        if (cmdTable.hasOption("g") || cmdTable.hasOption("debug")) {
            CmdConfiguration.debug = true
        }
        if (cmdTable.hasOption("classes")) {
            parseClasses(CmdConfiguration.classesList, cmdTable.getOptionValue("classes"))
        }
        if (cmdTable.hasOption("black-classes")) {
            parseClasses(CmdConfiguration.blackClasses, cmdTable.getOptionValue("black-classes"))
        }
        CmdConfiguration.dexFiles = cmdTable.argList
        configLog()
    } catch (e: Exception) {
        System.err.println("Argument error: ${e.message}")
        exitProcess(1)
    }
}

fun parseClasses(classesList: MutableList<String>, value: String) {
    if (value.startsWith("@")) {
        FileReader(value.substring(1)).useLines {
            classesList.addAll(it)
        }
    } else {
        value.split(';').forEach {
            classesList.add(it)
        }
    }
}

fun compileClass(classNode: ClassNode) {
    val path = CmdConfiguration.outDir + File.separator + classNode.clsInfo.className() + ".class"
    if (!File(path).parentFile.exists()) {
        File(path).parentFile.mkdirs()
    }
    val transformer = ClassTransformer(classNode, CmdConfiguration, path)
    transformer.visitClass().dump()
}

fun runMain(): Int {
    try {
        for (dexFile in CmdConfiguration.dexFiles) {
            val dexNode = DexNode.create(dexFile, CmdConfiguration)
                    ?: throw DecodeException("Create dex node failed: $dexFile")
            dexNode.loadClass()
            for (classNode in dexNode.classes) {
                compileClass(classNode)
            }
        }
        FluentLogger.forEnclosingClass().atInfo().log("All method success/fail: " +
                "${CmdConfiguration.successNum}/${CmdConfiguration.failedNum}")
        return 0
    } catch (e: Throwable) {
        FluentLogger.forEnclosingClass().atInfo().log("All method success/fail: " +
                "${CmdConfiguration.successNum}/${CmdConfiguration.failedNum}")
        e.printStackTrace()
    }
    return 1
}

fun main(args: Array<String>) {
    configFromOptions(args, createCmdTable())
    exitProcess(runMain())
}
