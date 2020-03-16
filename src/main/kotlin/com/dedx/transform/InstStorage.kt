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
