package com.dedx.transform

import com.dedx.dex.struct.DexNode
import com.dedx.utils.DecodeException

const val SymbolType = 1
const val SymbolTypeIndex = 2
const val StringIndex = 3
const val NumberLiteral = 4

open class SymbolInfo protected constructor(private val symbolIdentifier: Int) {

    companion object {
        fun create(type: SlotType, indent: Int) = SymbolInfo(indent).setType(type)

        fun create(number: Long, indent: Int) = SymbolInfo(indent).setNumber(number)

        fun equal(left: SymbolInfo, right: SymbolInfo): Boolean {
            if (left.symbolIdentifier != right.symbolIdentifier) {
                return false
            }
            if (left::type.isInitialized && right::type.isInitialized && left.type == right.type) {
                return true
            }
            if (left.number > -1 && left.number == right.number) {
                return true
            }
            return false
        }
    }

    private lateinit var type: SlotType
    private var number: Long = -1

    protected fun setType(t: SlotType) = also {
        it.type = t
    }

    protected fun setNumber(n: Long) = also {
        it.number = n
    }

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

    // get number as number literal
    fun getNumberLiteral() = if (symbolIdentifier == NumberLiteral && number > -1) number
    else throw DecodeException("This symbol not number literal")

    fun getNumberLiteralOrNull() = if (symbolIdentifier == NumberLiteral && number > -1) number else null

    // get number as index or number literal
    fun getNumber() = if (symbolIdentifier > SymbolType && number > -1) number else throw DecodeException("This symbol has no number")

    fun getNumberOrNull() = if (symbolIdentifier > SymbolType && number > -1) number else null
}

class SymbolArrayInfo() : SymbolInfo(SymbolType) {
    init {
        setType(SlotType.ARRAY)
    }

    private val subTypeList = ArrayList<SlotType>()

    constructor(types: Array<out SlotType>) : this() {
        subTypeList.addAll(types)
    }

    fun hasSubType() = subTypeList.isNotEmpty()

    fun addSubType(type: SlotType) = subTypeList.add(type)

    fun lastType() = subTypeList.last()

    fun firstType() = subTypeList.first()

    fun subSize() = subTypeList.size

    fun getType(index: Int) = subTypeList[index]
}