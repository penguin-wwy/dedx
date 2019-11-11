package com.dedx.test

import com.dedx.dex.struct.DexNode
import com.dedx.transform.ClassTransformer
import com.dedx.tools.Configuration

import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

String compileDexOneClass(String tag, String dexFile) throws Exception {
    println "Compile: $dexFile"
    def config = new Configuration()
    def dexNode = DexNode.create(dexFile, config)
    println 'Load class...'
    dexNode.loadClass()
    def baseClass = dexNode.getClass(tag)
    def filePath = dexFile.replace(".dex", ".class")
    println "Dump to: $filePath"
    def compiler = new ClassTransformer(baseClass, config, filePath)
    return compiler.visitClass().dump()
}

String createClassPath(String parent, String className) {
    return String.join(File.separator, parent, className) + ".class"
}

String createJar(String parent, List<String> files) {
    def jarPath = String.join(File.separator, parent, "classes.jar")
    def jos = new JarOutputStream(new FileOutputStream(jarPath))
    if (!parent.endsWith("/")) {
        parent = parent + "/"
    }
    for (file in files) {
        def className = file.replace(parent, "")
        def zipEntry = new ZipEntry(className)
        def classFile = new File(file)
        zipEntry.setTime(classFile.lastModified())
        jos.putNextEntry(zipEntry)
        def fis = new FileInputStream(classFile)
        def buffer = new byte[64 * 1024]
        int length = fis.read(buffer)
        while (length != -1) {
            jos.write(buffer, 0, length)
            length = fis.read(buffer)
        }
        fis.close()
        jos.closeEntry()
    }
    jos.close()
    return jarPath
}

String compileDexMultipClass(String tag, String dexFile) throws Exception {
    println "Compile: $dexFile"
    def parent = dexFile.replace(".dex", "")
    new File(parent).mkdirs()
    def config = new Configuration()
    def dexNode = DexNode.create(dexFile, config)
    dexNode.loadClass()
    def files = new ArrayList<String>()
    for (clazz in dexNode.classes) {
        def classPath = createClassPath(parent, clazz.clsInfo.className())
        def compiler = new ClassTransformer(clazz, config, classPath)
        files.add(compiler.visitClass().dump())
    }
    return createJar(parent, files)
}

boolean assertResult(tag, String classFile) {
    switch (tag) {
        case "com.test.Base": return BaseAssert(classFile)
        case "AClass" : return AClassAssert(classFile)
        case "BClass" : return BClassAssert(classFile)
        case "CClass" : return CClassAssert(classFile)
        case "TryCatchTest" : return TryCatchAssert(classFile)
        default: return true
    }
}

def BaseAssert(String classFile) {
    def loader = new DynamicClassLoader()
    def baseClass = loader.defineClass("com.test.Base", Files.readAllBytes(Paths.get(classFile)))
    def addInt = baseClass.getMethod("addInt", int.class, int.class)
    def getName = baseClass.getMethod("getName")
    assert addInt.invoke(null, 1, 2) == 3
    assert getName.invoke(baseClass.newInstance()).equals("com.test.Base")
    return true
}

def AClassAssert(String classFile) {
    def urls = new URL[1]
    urls[0] = new File(classFile).toURI().toURL()
    def loader = new URLClassLoader(urls, this.class.classLoader)
    def aClass = loader.loadClass("AClass")
    def getName = aClass.getMethod("getName")
    assert getName.invoke(aClass.newInstance()).equals("AClass")
    def test = aClass.getMethod("test")
    assert test.invoke(null)
    return true
}

/* TODO fix */
def BClassAssert(String classFile) {
    def urls = new URL[1]
    urls[0] = new File(classFile).toURI().toURL()
    def loader = new URLClassLoader(urls, this.class.classLoader)
    def bClass = loader.loadClass("BClass")
    def descript = bClass.getMethod("descript")
    for (con in bClass.getConstructors()) {
        println con.genericParameterTypes
    }
    def constructor = bClass.getConstructor(String.class, boolean.class)
    def instance = constructor.newInstance("BClass", true)
    assert descript.invoke(instance)
    return true
}
/************/

def CClassAssert(String classFile) {
    def loader = new DynamicClassLoader()
    def baseClass = loader.defineClass("CClass", Files.readAllBytes(Paths.get(classFile)))
    def toString = baseClass.getMethod("toString")
    def instance = baseClass.getConstructor(int.class).newInstance(1)
    assert toString.invoke(instance).equals("CClass1")
    return true
}

def TryCatchAssert(String classFile) {
    def loader = new DynamicClassLoader()
    def tryCatchClass = loader.defineClass("TryCatchTest", Files.readAllBytes(Paths.get(classFile)))
    def toString = tryCatchClass.getMethod("throwTest")
    assert toString.invoke(null).equals("Throw runtime")
    return true
}

static void main(String[] args) {
    println 'ScriptMain Groovy Script.'
    def dexFilesToClass = ['com.test.Base' : 'Base.dex', 'CClass' : 'CClass.dex', 'TryCatchTest' : 'TryCatchTest.dex']
    def dexFilesToJar = ['AClass' : 'AClasses.dex']
    try {
        for (entry in dexFilesToClass) {
            assert assertResult(entry.key, compileDexOneClass(entry.key, "${args[0]}/${entry.value}"))
        }
        for (entry in dexFilesToJar) {
            assert assertResult(entry.key, compileDexMultipClass(entry.key, "${args[0]}/${entry.value}"))
        }
    } catch(Exception e) {
        e.printStackTrace()
    }
}