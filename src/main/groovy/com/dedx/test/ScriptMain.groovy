package com.dedx.test

import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer

import java.nio.file.Files
import java.nio.file.Paths

String compileDex(String dexFile) throws Exception {
    println "Compile: $dexFile"
    def dexNode = DexNode.create(dexFile)
    println 'Load class...'
    dexNode.loadClass()
    def baseClass = dexNode.getClass("com.test.Base")
    def filePath = dexFile.replace(".dex", ".class")
    println "Dump to: $filePath"
    def compiler = new ClassTransformer(baseClass, filePath)
    return compiler.visitClass().dump()
}

boolean assertResult(tag, classFile) {
    switch (tag) {
        case "Base": return BaseAssert(classFile)
        default: return true
    }
}

def BaseAssert(String classFile) {
    def loader = new DynamicClassLoader()
    def baseClass = loader.defineClass("com.test.Base", Files.readAllBytes(Paths.get(classFile)))
    def addInt = baseClass.getMethod("addInt", int.class, int.class)
    println addInt.invoke(null, 1, 2)
}

static void main(String[] args) {
    println 'com.dedx.test.ScriptMain Groovy Script.'
    def dexFiles = ['Base' : 'Base.dex']

    try {
        for (entry in dexFiles) {
            assert assertResult(entry.key, compileDex("${args[0]}/${entry.value}"))
        }
    } catch(Exception e) {
        e.printStackTrace()
    }
}