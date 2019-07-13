package com.dedx.struct.type

enum class BasicType constructor(val signature: String, val typeName: String, val mark: Int) {
    VOID("V", "void", 0),
    BOOLEAN("Z", "boolean", 1),
    CHAR("C", "char", 2),
    BYTE("B", "byte", 3),
    SHORT("S", "short", 4),
    INT("I", "int", 5),
    FLOAT("F", "float", 6),
    DOUBLE("D", "double", 7),
    OBJECT("L", "object", 8),
    ARRAY("[", "array", 9);

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
}