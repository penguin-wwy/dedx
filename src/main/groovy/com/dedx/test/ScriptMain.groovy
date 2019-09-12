package com.dedx.test

class ScriptMain {
    static String[] dexFiles = ['Base.dex']

    static String compileDex(dexFile) {
        return ""
    }

    static boolean assertResult(classFile) {
        return true
    }

    static void main(String[] args) {
        println 'com.dedx.test.ScriptMain Groovy Script.'
        for (dexFile in dexFiles) {
            def classFile = compileDex(dexFile)
            assert assertResult(classFile)
        }
    }
}