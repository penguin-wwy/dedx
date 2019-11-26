package com.dedx.transform

import com.dedx.dex.struct.DexNode
import com.dedx.utils.DecodeException

const val SymbolType = 1
const val SymbolTypeIndex = 2
const val StringIndex = 3
const val NumberLiteral = 4

class SymbolInfo private constructor(private val symbolIdentifier: Int) {
    constructor(type: SlotType, identifier: Int): this(identifier) {
        this.type = type
    }
    constructor(index: Long, identifier: Int): this(identifier) {
        this.number = index
    }

    private lateinit var type: SlotType
    private var number: Long = -1

    fun isSymbolType() = symbolIdentifier == SymbolType
    fun isSymbolTypeIndex() = symbolIdentifier == SymbolTypeIndex
    fun isStringIndex() = symbolIdentifier == StringIndex
    fun isNumberLiteral() = symbolIdentifier == NumberLiteral

    fun getType() = if (this::type.isInitialized && symbolIdentifier == SymbolType)
        type else throw DecodeException("This symbol not type")

    fun getType(dex: DexNode) = if (this::type.isInitialized && symbolIdentifier == SymbolTypeIndex)
        dex.getType(number.toInt()) else throw DecodeException("This symbol not class type")

    fun getTypeOrNull() = if (this::type.isInitialized && symbolIdentifier == SymbolType) type else null

    fun getTypeOrNull(dex: DexNode) = if (this::type.isInitialized && symbolIdentifier == SymbolTypeIndex)
        dex.getType(number.toInt()) else null

    fun getString(dex: DexNode): String = if (symbolIdentifier == StringIndex && number > -1 && number < Int.MAX_VALUE)
        dex.getString(number.toInt()) else throw DecodeException("This symbol not string")

    fun getString(transformer: InstTransformer): String = if (symbolIdentifier == StringIndex && number > -1 && number < Int.MAX_VALUE)
        transformer.string(number.toInt()) else throw DecodeException("This symbol not string")

    fun getStringOrNull(dex: DexNode) = if (symbolIdentifier == StringIndex && number > -1 && number < Int.MAX_VALUE)
        dex.getString(number.toInt()) else null

    fun getStringOrNull(transformer: InstTransformer) = if (symbolIdentifier == StringIndex && number > -1 && number < Int.MAX_VALUE)
        transformer.string(number.toInt()) else null

    fun getNumber() = if (symbolIdentifier == NumberLiteral && number > -1) number else throw DecodeException("This symbol not number literal")

    fun getNumberOrNull() = if (symbolIdentifier == NumberLiteral && number > -1) number else null

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (other !is SymbolInfo) {
            return false
        }
        if (symbolIdentifier != other.symbolIdentifier) {
            return false
        }
        if (this::type.isInitialized && other::type.isInitialized && type.equals(other.type)) {
            return true
        }
        if (number != -1L && other.number != -1L && number == other.number) {
            return true
        }
        return false
    }
}