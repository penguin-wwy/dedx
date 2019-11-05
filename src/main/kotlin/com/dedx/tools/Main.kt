package com.dedx.tools

import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer
import com.dedx.utils.DecodeException
import org.apache.commons.cli.*
import java.io.File
import java.lang.RuntimeException
import java.util.*
import java.util.logging.ConsoleHandler
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess

fun configLog() {
    val root = Logger.getLogger("")
    root.level = if (Configuration.debug) Level.FINEST else Level.INFO
    root.handlers.forEach { root.removeHandler(it) }
    root.addHandler(Configuration.logFile?.let {
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
            .longOpt("opt").hasArg().argName("[fast|normal|optimize]")
            .desc("Specify optimization level").build()
    val logFile = Option.builder().longOpt("log").desc("Specify log file").hasArg().build()
    val debug = Option.builder("g").longOpt("debug").desc("Print debug info").build()

    val optTable = Options()
    optTable.addOption(help)
    optTable.addOption(version)
    optTable.addOption(output)
    optTable.addOption(optLevel)
    optTable.addOption(logFile)
    optTable.addOption(debug)
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
            Configuration.outDir = cmdTable.getOptionValue("o") ?: cmdTable.getOptionValue("output")
        }
        if (cmdTable.hasOption("opt")) {
            Configuration.optLevel = when (cmdTable.getOptionValue("opt")) {
                "fast" -> Configuration.NormalFast
                "normal" -> Configuration.NormalOpt
                "optimize" -> Configuration.Optimized
                else -> {
                    throw RuntimeException("Invalid parameter for 'opt'")
                }
            }
        }
        if (cmdTable.hasOption("log")) {
            Configuration.logFile = cmdTable.getOptionValue("log")
        }
        if (cmdTable.hasOption("g") || cmdTable.hasOption("debug")) {
            Configuration.debug = true
        }
        Configuration.dexFiles = cmdTable.argList
        configLog()
    } catch (e: Exception) {
        System.err.println("Argument error: ${e.message}")
        exitProcess(1)
    }
}

fun compileClass(classNode: ClassNode) {
    val path = Configuration.outDir + File.separator + classNode.clsInfo.className() + ".class"
    if (!File(path).parentFile.exists()) {
        File(path).parentFile.mkdirs()
    }
    val transformer = ClassTransformer(classNode, path)
    transformer.visitClass().dump()
}

fun runMain(): Int {
    try {
        for (dexFile in Configuration.dexFiles) {
            val dexNode = DexNode.create(dexFile) ?: throw DecodeException("Create dex node failed: $dexFile")
            dexNode.loadClass()
            for (classNode in dexNode.classes) {
                compileClass(classNode)
            }
        }
        return 0
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    return 1
}

fun main(args: Array<String>) {
    configFromOptions(args, createCmdTable())
    exitProcess(runMain())
}