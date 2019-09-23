package com.dedx.tools

import com.dedx.dex.struct.ClassNode
import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer
import com.dedx.utils.DecodeException
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import java.io.File

fun createCmdTable(): Options {
    val help = Option.builder("h")
            .longOpt("help").desc("Print help message").build()
    val version = Option.builder("v")
            .longOpt("version").desc("Print version").build()
    val output = Option.builder("o")
            .longOpt("output")
            .hasArg().argName("dirname").desc("Specify output dirname").build()
    val optFast = Option.builder().longOpt("fast").build()
    val optNor = Option.builder().longOpt("normal").build()
    val optOpt = Option.builder().longOpt("optimize").build()

    val optTable = Options()
    optTable.addOption(help)
    optTable.addOption(version)
    optTable.addOption(output)
    optTable.addOption(optFast)
    optTable.addOption(optNor)
    optTable.addOption(optOpt)
    return optTable
}

fun configFromOptions(args: Array<String>, optTable: Options) {
    val parser = DefaultParser()
    try {
        val cmdTable = parser.parse(optTable, args)
        if (cmdTable.hasOption("v") || cmdTable.hasOption("version")) {
            System.exit(0)
        }
        if (cmdTable.hasOption("h") || cmdTable.hasOption("help")) {
            HelpFormatter().printHelp("command [options] <dexfile>", optTable)
            System.exit(0)
        }
        if (cmdTable.hasOption("o") || cmdTable.hasOption("output")) {
            Configuration.outDir = cmdTable.getOptionValue("o") ?: cmdTable.getOptionValue("output")
        }
        if (cmdTable.hasOption("fast")) {
            Configuration.optLevel = Configuration.NormalFast
        }
        if (cmdTable.hasOption("normal")) {
            Configuration.optLevel = Configuration.NormalOpt
        }
        if (cmdTable.hasOption("optimize")) {
            Configuration.optLevel = Configuration.Optimized
        }
        Configuration.dexFiles = cmdTable.argList
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun compileClass(classNode: ClassNode) {
    val path = Configuration.outDir + File.pathSeparator + classNode.clsInfo.className()
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
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 1
}

fun main(args: Array<String>) {
    configFromOptions(args, createCmdTable())
    System.exit(runMain())
}