package com.monaflow.app.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun `parse to minor units`() {
        assertEquals(1250L, MoneyUtils.parseToMinor("12.5", "CNY"))
        assertEquals(1250L, MoneyUtils.parseToMinor("12.50", "CNY"))
        assertEquals(999L, MoneyUtils.parseToMinor("9.99", "USD"))
        assertEquals(12800L, MoneyUtils.parseToMinor("128", "CNY"))
        assertNull(MoneyUtils.parseToMinor("", "CNY"))
        assertNull(MoneyUtils.parseToMinor("abc", "CNY"))
        assertNull(MoneyUtils.parseToMinor("12.5.6", "CNY"))
    }

    @Test
    fun `jpy and krw have zero decimals`() {
        assertEquals(1000L, MoneyUtils.parseToMinor("1000", "JPY"))
        assertEquals(999L, MoneyUtils.parseToMinor("999", "JPY"))
        assertEquals("1000", MoneyUtils.format(1000L, "JPY"))
        assertEquals(0, MoneyUtils.decimalsOf("KRW"))
        assertEquals(0, MoneyUtils.decimalsOf("JPY"))
        assertEquals(2, MoneyUtils.decimalsOf("CNY"))
    }

    @Test
    fun `round trip format parse`() {
        listOf(
            0L to "CNY",
            1L to "CNY",
            1250L to "CNY",
            99999999L to "CNY",
            5L to "JPY",
            123456L to "USD",
        ).forEach { (minor, currency) ->
            assertEquals(minor, MoneyUtils.parseToMinor(MoneyUtils.format(minor, currency), currency))
        }
    }

    @Test
    fun `format negative and symbols`() {
        assertEquals("-12.50", MoneyUtils.format(-1250L, "CNY"))
        assertEquals("¥12.50", MoneyUtils.formatWithSymbol(1250L, "CNY"))
        assertEquals("$9.99", MoneyUtils.formatWithSymbol(999L, "USD"))
        assertEquals("￥1000", MoneyUtils.formatWithSymbol(1000L, "JPY"))
    }
}
