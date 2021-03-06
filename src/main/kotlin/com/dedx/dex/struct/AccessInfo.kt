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

package com.dedx.dex.struct

import com.android.dx.rop.code.AccessFlags

interface AccessInfo {
    val accFlags: Int
    fun isPublic() = (accFlags and AccessFlags.ACC_PUBLIC) != 0
    fun isProtected() = (accFlags and AccessFlags.ACC_PROTECTED) != 0
    fun isPrivate() = (accFlags and AccessFlags.ACC_PRIVATE) != 0
    fun isFinal() = (accFlags and AccessFlags.ACC_FINAL) != 0
    fun isStatic() = (accFlags and AccessFlags.ACC_STATIC) != 0
    fun isAbstract() = (accFlags and AccessFlags.ACC_ABSTRACT) != 0
    fun isInterface() = (accFlags and AccessFlags.ACC_INTERFACE) != 0
}
