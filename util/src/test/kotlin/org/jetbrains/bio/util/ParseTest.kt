package org.jetbrains.bio.util

import org.junit.Test
import kotlin.test.assertEquals

/**
 * @author Oleg Shpynov
 * @since 01/04/16.
 */
class ParseTest {

    @Test fun testParseInt() {
        val tokenizer = Tokenizer("123456", emptySet())
        assertEquals(123456, tokenizer.parseInt())
    }

    @Test fun testParseDouble() {
        val tokenizer = Tokenizer("123456.5", emptySet())
        assertEquals(123456.5, tokenizer.parseDouble())
    }


    val KEYWORDS = setOf(Tokenizer.LBRACE, Tokenizer.RBRACE, Tokenizer.COMMA)

    @Test fun testParseIntStep1() {
        val tokenizer = Tokenizer("1", KEYWORDS)
        assertEquals("[1]", tokenizer.parseIntOrStep().toString())
    }

    @Test fun testParseIntStep2() {
        val tokenizer = Tokenizer("{1,10,1}", KEYWORDS)
        assertEquals("[1, 2, 3, 4, 5, 6, 7, 8, 9]", tokenizer.parseIntOrStep().toString())
    }

    @Test fun testParseDoubleStep1() {
        val tokenizer = Tokenizer("1.5", KEYWORDS)
        assertEquals("[1.5]", tokenizer.parseDoubleOrStep().toString())
    }

    @Test fun testParseDoubleStep2() {
        val tokenizer = Tokenizer("{0,1,0.5}", KEYWORDS)
        assertEquals("[0.0, 0.5]", tokenizer.parseDoubleOrStep().toString())
    }

    @Test fun testRegex() {
        val DELIMITER = RegexLexeme("[_;, ]|\\.\\.")
        val tokenizer = Tokenizer("1_2;3,4 5..6", setOf(DELIMITER))
        assertEquals(1, tokenizer.parseInt())
        val list = mutableListOf(1)
        while (tokenizer.fetch() == DELIMITER) {
            tokenizer.next()
            list.add(tokenizer.parseInt())
        }
        assertEquals("[1, 2, 3, 4, 5, 6]", list.toString())
    }

}