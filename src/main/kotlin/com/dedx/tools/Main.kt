package com.dedx.tools

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

fun createCmdTable(): Options {
    val help = Option.builder("h")
            .longOpt("help").desc("Print help message").build()
    val version = Option.builder("v")
            .longOpt("version").desc("Print version").build()
    val output = Option.builder("o")
            .longOpt("output")
            .hasArg().argName("dirname").desc("Specify output dirname").build()

    val optTable = Options()
    optTable.addOption(help)
    optTable.addOption(version)
    optTable.addOption(output)

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
        Configuration.dexFiles = cmdTable.argList
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun main(args: Array<String>) {
    configFromOptions(args, createCmdTable())
}