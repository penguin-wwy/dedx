package com.dedx.transform

import org.objectweb.asm.Label

interface InstStorage {
    fun storeInst(labelInst: LabelInst, jvmInst: JvmInst)

    fun getInst(labelInst: LabelInst): JvmInst?
}

class LabelMap : InstStorage {
    private val label2Inst = HashMap<Label, JvmInst>()

    override fun storeInst(labelInst: LabelInst, jvmInst: JvmInst) {
        label2Inst[labelInst.getValueOrCreate()] = jvmInst
    }

    override fun getInst(labelInst: LabelInst) = label2Inst[labelInst.getValue()]
}