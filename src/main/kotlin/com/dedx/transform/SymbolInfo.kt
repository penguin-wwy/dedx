package com.dedx.transform

import com.dedx.dex.struct.DexNode
import com.dedx.utils.DecodeException
import java.lang.StringBuilder

//const val SymbolType = 1
//const val SymbolTypeIndex = 2
//const val StringIndex = 3
//const val NumberLiteral = 4

enum class SymIdentifier {
    SymbolType,
    SymbolTypeIndex,
    StringIndex,
    NumberLiteral
}

open class SymbolInfo protected constructor(private val symbolIdentifier: SymIdentifier) {

    companion object {
        fun create(type: SlotType, indent: SymIdentifier) = SymbolInfo(indent).setType(type)

        fun create(number: Long, indent: SymIdentifier) = SymbolInfo(indent).setNumber(number)

        fun equal(left: SymbolInfo, right: SymbolInfo): Boolean {
            if (left.symbolIdentifier != right.symbolIdentifier) {
                return false
            }
            if (left::type.isInitialized && right::type.isInitialized && left.type == right.type) {
                return true
            }
            if (left.number != null && left.number == right.number) {
                return true
            }
            return false
        }
    }

    private lateinit var sourceDex: DexNode
    private lateinit var type: SlotType
    private var number: Long? = null

    public fun setSourceDex(dex: DexNode) = also {
        sourceDex = dex
    }

    protected fun setType(t: SlotType) = also {
        it.type = t
    }

    protected fun setNumber(n: Long) = also {
        it.number = n
    }

    fun isSymbolType() = symbolIdentifier == SymIdentifier.SymbolType
    fun isSymbolTypeIndex() = symbolIdentifier == SymIdentifier.SymbolTypeIndex
    fun isStringIndex() = symbolIdentifier == SymIdentifier.StringIndex
    fun isNumberLiteral() = symbolIdentifier == SymIdentifier.NumberLiteral

    fun getType() = if (this::type.isInitialized && symbolIdentifier == SymIdentifier.SymbolType)
        type else throw DecodeException("This symbol not type")

    fun getType(dex: DexNode) = if (this::type.isInitialized && symbolIdentifier == SymIdentifier.SymbolTypeIndex)
        dex.getType(getNumber().toInt()) else throw DecodeException("This symbol not class type")

    fun getTypeOrNull() = if (this::type.isInitialized && symbolIdentifier == SymIdentifier.SymbolType) type else null

    fun getTypeOrNull(dex: DexNode) = if (this::type.isInitialized && symbolIdentifier == SymIdentifier.SymbolTypeIndex)
        dex.getType(getNumber().toInt()) else null

    fun getString(dex: DexNode): String = if (symbolIdentifier == SymIdentifier.StringIndex && number != null)
        dex.getString(getNumber().toInt()) else throw DecodeException("This symbol not string")

    fun getString(transformer: InstTransformer): String = if (symbolIdentifier == SymIdentifier.StringIndex && number != null)
        transformer.string(getNumber().toInt()) else throw DecodeException("This symbol not string")

    fun getStringOrNull(dex: DexNode) = if (symbolIdentifier == SymIdentifier.StringIndex && number != null)
        dex.getString(getNumber().toInt()) else null

    fun getStringOrNull(transformer: InstTransformer) = if (symbolIdentifier == SymIdentifier.StringIndex && number != null)
        transformer.string(getNumber().toInt()) else null

    // get number as number literal
    fun getNumberLiteral() = if (symbolIdentifier == SymIdentifier.NumberLiteral && number != null) number
    else throw DecodeException("This symbol not number literal")

    fun getNumberLiteralOrNull() = if (symbolIdentifier == SymIdentifier.NumberLiteral && number != null) number else null

    // get number as index or number literal
    fun getNumber() = if (symbolIdentifier > SymIdentifier.SymbolType && number != null) number as Long else throw DecodeException("This symbol has no number")

    fun getNumberOrNull() = if (symbolIdentifier > SymIdentifier.SymbolType && number != null) number else null

    fun toString(dex: DexNode): String {
        val outString = StringBuilder("<$symbolIdentifier> ")
        when (symbolIdentifier) {
            SymIdentifier.SymbolType -> outString.append("$type")
            SymIdentifier.SymbolTypeIndex -> outString.append("${getType(dex)}")
            SymIdentifier.StringIndex -> outString.append(getString(dex))
            SymIdentifier.NumberLiteral -> outString.append("${getNumberLiteral()}")
        }
        return outString.toString()
    }

    override fun toString(): String {
        if (this::sourceDex.isInitialized) {
            return toString(sourceDex)
        }
        if (symbolIdentifier == SymIdentifier.SymbolType) {
            return "<${SymIdentifier.SymbolType}> $type"
        } else {
            return "<$symbolIdentifier> $number"
        }
    }
}

class SymbolArrayInfo() : SymbolInfo(SymIdentifier.SymbolType) {
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