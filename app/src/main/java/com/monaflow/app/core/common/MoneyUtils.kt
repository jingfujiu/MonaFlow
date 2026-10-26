package com.monaflow.app.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 金额处理：内部一律使用最小货币单位 Long（分），禁止浮点参与货币运算。
 * JPY/KRW 无小数位。
 */
object MoneyUtils {

    val SUPPORTED = listOf("CNY", "USD", "EUR", "JPY", "GBP", "HKD", "KRW", "TWD")

    private val SYMBOLS = mapOf(
        "CNY" to "¥",
        "USD" to "$",
        "EUR" to "€",
        "JPY" to "￥",
        "GBP" to "£",
        "HKD" to "HK$",
        "KRW" to "₩",
        "TWD" to "NT$",
    )

    fun decimalsOf(currency: String): Int = if (currency == "JPY" || currency == "KRW") 0 else 2

    fun symbolOf(currency: String): String = SYMBOLS[currency] ?: "$currency "

    /** 12.5 → 1250（CNY）；解析失败返回 null。 */
    fun parseToMinor(text: String, currency: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val dec = BigDecimal(trimmed).setScale(decimalsOf(currency), RoundingMode.HALF_UP)
            dec.movePointRight(decimalsOf(currency)).longValueExact()
        }.getOrNull()
    }

    fun format(minor: Long, currency: String): String {
        val dec = decimalsOf(currency)
        val abs = kotlin.math.abs(minor)
        val intPart = abs / powerOf10(dec)
        val frac = abs % powerOf10(dec)
        val sign = if (minor < 0) "-" else ""
        return if (dec == 0) {
            "$sign$intPart"
        } else {
            val fracStr = frac.toString().padStart(dec, '0')
            "$sign$intPart.$fracStr"
        }
    }

    fun formatWithSymbol(minor: Long, currency: String): String =
        symbolOf(currency) + format(minor, currency)

    private fun powerOf10(n: Int): Long {
        var v = 1L
        repeat(n) { v *= 10 }
        return v
    }
}
