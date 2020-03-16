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

package com.dedx.dex.struct.type

enum class BasicType constructor(val signature: String, val typeName: String, val mark: Int) {
    VOID("V", "void", 0),
    BOOLEAN("Z", "boolean", 1),
    CHAR("C", "char", 2),
    BYTE("B", "byte", 3),
    SHORT("S", "short", 4),
    INT("I", "int", 5),
    LONG("J", "long", 6),
    FLOAT("F", "float", 7),
    DOUBLE("D", "double", 8),
    OBJECT("L", "object", 9),
    ARRAY("[", "array", 10) {
        override fun toString(): String {
            return "[]"
        }
    };

    companion object {
        fun get(c: Char): BasicType? {
            for (basic in enumValues<BasicType>()) {
                if (basic.signature.equals(c.toString())) {
                    return basic
                }
            }
            return null
        }
    }

    fun descriptor() = signature

    override fun toString(): String {
        return typeName
    }
}
