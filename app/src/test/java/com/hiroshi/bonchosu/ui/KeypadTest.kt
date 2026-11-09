package com.hiroshi.bonchosu.ui

import com.hiroshi.bonchosu.ui.screens.Keypad
import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadTest {

    private fun type(vararg keys: String) = keys.fold("") { digits, key -> Keypad.apply(digits, key) }

    @Test
    fun `digits and 000 build the amount`() {
        assertEquals("45000", type("4", "5", "000"))
    }

    @Test
    fun `leading zeros are ignored`() {
        assertEquals("", type("0", "000"))
        assertEquals("7", type("0", "7"))
    }

    @Test
    fun `backspace removes one digit and holding it clears`() {
        assertEquals("4500", type("4", "5", "000", Keypad.BACK))
        assertEquals("", type("4", "5", Keypad.CLEAR))
        assertEquals("", type(Keypad.BACK))
    }

    @Test
    fun `amounts stop at 13 digits`() {
        assertEquals("1000000000000", type("1", *Array(6) { "000" }))
    }
}
